package com.sih.sif.service;

import com.sih.sif.dto.AnalyzeReportRequest;
import com.sih.sif.dto.DashboardStatsResponse;
import com.sih.sif.dto.PagedResponse;
import com.sih.sif.dto.SafetyReportResponse;
import com.sih.sif.exception.AiServiceUnavailableException;
import com.sih.sif.model.SafetyReport;
import com.sih.sif.repository.SafetyReportRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core Business Service for Safety Report Analysis and Management.
 * 
 * Key Responsibilities:
 * 1. Orchestrates the integration between Spring Boot and the Python FastAPI AI service.
 * 2. Transmits safety reports to the Python NLP model over HTTP REST.
 * 3. Stores rich attributes (factors, actions, explanations) as native MongoDB arrays/objects.
 * 4. Persists analysed reports to MongoDB Atlas.
 * 5. Computes live dashboard aggregations using Mongo pipelines.
 * 6. Seeds demo benchmark cases asynchronously on application startup when enabled.
 */
@Service
public class SafetyReportService {

    private static final Logger log = LoggerFactory.getLogger(SafetyReportService.class);

    private final SafetyReportRepository repository;
    private final MongoTemplate mongoTemplate;
    private final RestTemplate restTemplate;

    @Value("${ai.service.url:http://127.0.0.1:8000}")
    private String aiServiceUrl;

    @Value("${ai.service.api-key:}")
    private String aiServiceApiKey;

    @Value("${app.seed-demo-data:true}")
    private boolean seedDemoData;

    public SafetyReportService(SafetyReportRepository repository, MongoTemplate mongoTemplate, RestTemplate restTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.restTemplate = restTemplate;
    }

    public SafetyReportResponse analyzeAndSave(AnalyzeReportRequest request) {
        String predictUrl = aiServiceUrl + "/predict";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (aiServiceApiKey != null && !aiServiceApiKey.isBlank()) {
            headers.set("X-API-Key", aiServiceApiKey.trim());
        }

        Map<String, Object> aiReq = new HashMap<>();
        aiReq.put("report", request.getReport());
        aiReq.put("report_type", request.getReportType());
        aiReq.put("location", request.getLocation());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(aiReq, headers);
        
        Map<String, Object> body;
        try {
            ResponseEntity<Map> aiResponse = restTemplate.postForEntity(predictUrl, entity, Map.class);
            body = aiResponse.getBody();
        } catch (ResourceAccessException e) {
            log.error("Timeout or connection failure connecting to AI service at {}: {}", predictUrl, e.getMessage());
            throw new AiServiceUnavailableException("AI service is currently unavailable or timed out. Please retry in a moment.", e);
        } catch (HttpStatusCodeException e) {
            log.error("AI service returned HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AiServiceUnavailableException("AI service returned error: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Unexpected error calling AI service: {}", e.getMessage(), e);
            throw new AiServiceUnavailableException("Failed to communicate with AI inference engine.", e);
        }

        if (body == null) {
            log.error("Empty response body received from AI service at {}", predictUrl);
            throw new AiServiceUnavailableException("Empty response received from AI Service");
        }

        SafetyReport report = new SafetyReport();
        report.setReportText(request.getReport());
        report.setReportType(request.getReportType());
        report.setLocation(request.getLocation());

        Object precursorDetectedObj = body.get("sif_precursor_detected");
        report.setSifPrecursorDetected(precursorDetectedObj instanceof Boolean ? (Boolean) precursorDetectedObj : Boolean.FALSE);

        Object precursorTypeObj = body.get("precursor_type");
        report.setPrecursorType(precursorTypeObj != null ? String.valueOf(precursorTypeObj) : "OTHER");
        
        Object riskScoreObj = body.get("risk_score");
        report.setRiskScore(riskScoreObj instanceof Number ? ((Number) riskScoreObj).doubleValue() : 0.0);

        Object riskLevelObj = body.get("risk_level");
        report.setRiskLevel(riskLevelObj != null ? String.valueOf(riskLevelObj) : "LOW");
        
        Object confObj = body.get("confidence");
        report.setConfidence(confObj instanceof Number ? ((Number) confObj).doubleValue() : 0.0);

        // Store lists natively in MongoDB
        if (body.get("detected_factors") instanceof List) {
            report.setDetectedFactors(((List<?>) body.get("detected_factors")).stream().map(String::valueOf).collect(Collectors.toList()));
        }
        if (body.get("potential_consequences") instanceof List) {
            report.setPotentialConsequences(((List<?>) body.get("potential_consequences")).stream().map(String::valueOf).collect(Collectors.toList()));
        }
        if (body.get("recommended_actions") instanceof List) {
            report.setRecommendedActions(((List<?>) body.get("recommended_actions")).stream().map(String::valueOf).collect(Collectors.toList()));
        }
        if (body.get("top_terms") instanceof List) {
            List<Map<String, Object>> termList = new ArrayList<>();
            for (Object item : (List<?>) body.get("top_terms")) {
                if (item instanceof Map) {
                    termList.add((Map<String, Object>) item);
                }
            }
            report.setTopTerms(termList);
        }
        if (body.get("explanation") instanceof List) {
            report.setExplanation(((List<?>) body.get("explanation")).stream().map(String::valueOf).collect(Collectors.toList()));
        }

        SafetyReport saved = repository.save(report);
        Object disclaimerObj = body.get("decision_support_disclaimer");
        String disclaimer = disclaimerObj != null ? String.valueOf(disclaimerObj) : null;
        return mapToResponse(saved, disclaimer);
    }

    public PagedResponse<SafetyReportResponse> getReportsPaged(int page, int size) {
        int clampedSize = Math.max(1, Math.min(size, 100));
        int validPage = Math.max(0, page);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(validPage, clampedSize);
        org.springframework.data.domain.Page<SafetyReport> paged = repository.findAllByOrderByCreatedAtDesc(pageable);

        List<SafetyReportResponse> items = paged.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return new PagedResponse<>(items, paged.getNumber(), paged.getSize(), paged.getTotalElements(), paged.getTotalPages());
    }

    public PagedResponse<SafetyReportResponse> getHighRiskReportsPaged(int page, int size) {
        int clampedSize = Math.max(1, Math.min(size, 100));
        int validPage = Math.max(0, page);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(validPage, clampedSize);
        org.springframework.data.domain.Page<SafetyReport> paged = repository.findByRiskLevelInOrderByCreatedAtDesc(Arrays.asList("HIGH", "CRITICAL"), pageable);

        List<SafetyReportResponse> items = paged.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return new PagedResponse<>(items, paged.getNumber(), paged.getSize(), paged.getTotalElements(), paged.getTotalPages());
    }

    public List<SafetyReportResponse> getAllReports() {
        return repository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<SafetyReportResponse> getHighRiskReports() {
        return repository.findByRiskLevelInOrderByCreatedAtDesc(Arrays.asList("HIGH", "CRITICAL"))
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Aggregates real-time statistics using MongoDB Aggregation pipelines ($match, $group).
     */
    public DashboardStatsResponse getDashboardStats() {
        DashboardStatsResponse stats = new DashboardStatsResponse();
        long total = repository.count();
        long sifCount = repository.countBySifPrecursorDetectedTrue();
        long high = repository.countByRiskLevel("HIGH");
        long crit = repository.countByRiskLevel("CRITICAL");

        stats.setTotalReports(total);
        stats.setSifPrecursors(sifCount);
        stats.setHighRiskReports(high);
        stats.setCriticalReports(crit);

        // Precursor distribution aggregation: $match non-null precursorType -> $group by precursorType with $sum
        Aggregation precursorAgg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("precursorType").ne(null).ne("")),
                Aggregation.group("precursorType").count().as("count")
        );
        AggregationResults<Document> precursorResults = mongoTemplate.aggregate(precursorAgg, "safety_reports", Document.class);
        Map<String, Long> precDist = new HashMap<>();
        for (Document doc : precursorResults.getMappedResults()) {
            String key = doc.getString("_id");
            Number countNum = doc.get("count", Number.class);
            if (key != null && countNum != null) {
                precDist.put(key, countNum.longValue());
            }
        }
        stats.setPrecursorDistribution(precDist);

        // Risk distribution aggregation: $match non-null riskLevel -> $group by riskLevel with $sum
        Aggregation riskAgg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("riskLevel").ne(null).ne("")),
                Aggregation.group("riskLevel").count().as("count")
        );
        AggregationResults<Document> riskResults = mongoTemplate.aggregate(riskAgg, "safety_reports", Document.class);
        Map<String, Long> riskDist = new HashMap<>();
        for (Document doc : riskResults.getMappedResults()) {
            String key = doc.getString("_id");
            Number countNum = doc.get("count", Number.class);
            if (key != null && countNum != null) {
                riskDist.put(key, countNum.longValue());
            }
        }
        stats.setRiskDistribution(riskDist);

        return stats;
    }

    private SafetyReportResponse mapToResponse(SafetyReport report) {
        return mapToResponse(report, "Prototype Decision Support: Requires safety officer review.");
    }

    private SafetyReportResponse mapToResponse(SafetyReport report, String disclaimer) {
        SafetyReportResponse resp = new SafetyReportResponse();
        resp.setId(report.getId());
        resp.setReportText(report.getReportText());
        resp.setReportType(report.getReportType());
        resp.setLocation(report.getLocation());
        resp.setSifPrecursorDetected(report.getSifPrecursorDetected());
        resp.setPrecursorType(report.getPrecursorType());
        resp.setRiskScore(report.getRiskScore());
        resp.setRiskLevel(report.getRiskLevel());
        resp.setConfidence(report.getConfidence());
        resp.setCreatedAt(report.getCreatedAt());
        resp.setDecisionSupportDisclaimer(disclaimer);

        resp.setDetectedFactors(report.getDetectedFactors() != null ? report.getDetectedFactors() : new ArrayList<>());
        resp.setPotentialConsequences(report.getPotentialConsequences() != null ? report.getPotentialConsequences() : new ArrayList<>());
        resp.setRecommendedActions(report.getRecommendedActions() != null ? report.getRecommendedActions() : new ArrayList<>());
        resp.setTopTerms(report.getTopTerms() != null ? report.getTopTerms() : new ArrayList<>());
        resp.setExplanation(report.getExplanation() != null ? report.getExplanation() : new ArrayList<>());

        return resp;
    }

    /**
     * Seeds initial demo data asynchronously on ApplicationReadyEvent.
     * Prevents startup delays when the AI service is warming up or slow.
     * Gated by app.seed-demo-data (SEED_DEMO_DATA env var).
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void seedInitialDemoData() {
        if (!seedDemoData) {
            log.info("Demo data seeding is disabled via configuration (app.seed-demo-data=false)");
            return;
        }

        if (repository.count() == 0) {
            log.info("Starting asynchronous initial demo data seeding...");
            List<AnalyzeReportRequest> seedCases = new ArrayList<>();

            AnalyzeReportRequest c1 = new AnalyzeReportRequest();
            c1.setReport("Worker fell 15 feet from an unsecured ladder without harness while painting exterior tank shell.");
            c1.setReportType("Unsafe Act / Condition");
            c1.setLocation("OIL Rig Alpha, Duliajan");
            seedCases.add(c1);

            AnalyzeReportRequest c2 = new AnalyzeReportRequest();
            c2.setReport("Electrician was working on an energized 480V breaker panel without lockout tagout or voltage testing.");
            c2.setReportType("Unsafe Act");
            c2.setLocation("Gas Compression Plant 2, Moran");
            seedCases.add(c2);

            AnalyzeReportRequest c3 = new AnalyzeReportRequest();
            c3.setReport("Two workers entered crude oil storage tank for cleaning without atmospheric gas testing or ventilation.");
            c3.setReportType("Unsafe Condition");
            c3.setLocation("Crude Tank Farm, Digboi");
            seedCases.add(c3);

            AnalyzeReportRequest c4 = new AnalyzeReportRequest();
            c4.setReport("Trash and empty cardboard boxes left in hallway near office doorway obstructing walkway.");
            c4.setReportType("Near Miss");
            c4.setLocation("Administrative Building, Guwahati");
            seedCases.add(c4);

            AnalyzeReportRequest c5 = new AnalyzeReportRequest();
            c5.setReport("Forklift operator was driving in reverse with obstructed view and nearly collided with a pedestrian worker.");
            c5.setReportType("Near Miss");
            c5.setLocation("Central Warehouse, Duliajan");
            seedCases.add(c5);

            for (AnalyzeReportRequest req : seedCases) {
                try {
                    analyzeAndSave(req);
                } catch (Exception e) {
                    log.info("Seed notice (AI service might still be warming up): {}", e.getMessage());
                }
            }
            log.info("Completed asynchronous demo data seeding check.");
        }
    }
}

