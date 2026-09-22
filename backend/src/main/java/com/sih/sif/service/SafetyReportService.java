package com.sih.sif.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.sif.dto.AnalyzeReportRequest;
import com.sih.sif.dto.DashboardStatsResponse;
import com.sih.sif.dto.SafetyReportResponse;
import com.sih.sif.model.SafetyReport;
import com.sih.sif.repository.SafetyReportRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core Business Service for Safety Report Analysis and Management.
 * 
 * Key Responsibilities:
 * 1. Orchestrates the integration between Spring Boot and the Python FastAPI AI service.
 * 2. Transmits safety reports to the Python NLP model over HTTP REST.
 * 3. Serializes and deserializes rich JSON payload attributes (factors, actions, explanations).
 * 4. Persists analysed reports to the embedded H2 SQL database via JPA.
 * 5. Computes live dashboard aggregations across all recorded incidents.
 * 6. Seeds realistic benchmark cases on initial application startup.
 */
@Service
public class SafetyReportService {

    private final SafetyReportRepository repository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Injected AI service URL from application.properties, defaulting to localhost:8000
    @Value("${ai.service.url:http://127.0.0.1:8000}")
    private String aiServiceUrl;

    /**
     * Constructor injection for Spring Data repository and initialization of JSON and HTTP clients.
     */
    public SafetyReportService(SafetyReportRepository repository) {
        this.repository = repository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Primary business workflow:
     * 1. Constructs an HTTP POST request targeting the Python FastAPI /predict endpoint.
     * 2. Receives model-inferred SIF precursor, risk level, confidence, and explanations.
     * 3. Maps and persists the safety incident into the H2 database.
     * 4. Converts the persisted entity into a strongly-typed API response DTO.
     */
    public SafetyReportResponse analyzeAndSave(AnalyzeReportRequest request) {
        // Build the target endpoint URL
        String predictUrl = aiServiceUrl + "/predict";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Prepare JSON request map for Python AI Service
        Map<String, Object> aiReq = new HashMap<>();
        aiReq.put("report", request.getReport());
        aiReq.put("report_type", request.getReportType());
        aiReq.put("location", request.getLocation());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(aiReq, headers);
        
        // Execute synchronous HTTP call to Python AI engine
        ResponseEntity<Map> aiResponse = restTemplate.postForEntity(predictUrl, entity, Map.class);
        Map<String, Object> body = aiResponse.getBody();

        if (body == null) {
            throw new RuntimeException("Empty response received from Python AI Service");
        }

        // Instantiate new entity and populate with report metadata
        SafetyReport report = new SafetyReport();
        report.setReportText(request.getReport());
        report.setReportType(request.getReportType());
        report.setLocation(request.getLocation());

        // Extract ML classification outputs
        report.setSifPrecursorDetected((Boolean) body.get("sif_precursor_detected"));
        report.setPrecursorType((String) body.get("precursor_type"));
        
        Object riskScoreObj = body.get("risk_score");
        report.setRiskScore(riskScoreObj instanceof Number ? ((Number) riskScoreObj).doubleValue() : 0.0);
        report.setRiskLevel((String) body.get("risk_level"));
        
        Object confObj = body.get("confidence");
        report.setConfidence(confObj instanceof Number ? ((Number) confObj).doubleValue() : 0.0);

        // Convert rich lists/maps to JSON strings for database column storage
        try {
            report.setDetectedFactors(objectMapper.writeValueAsString(body.get("detected_factors")));
            report.setPotentialConsequences(objectMapper.writeValueAsString(body.get("potential_consequences")));
            report.setRecommendedActions(objectMapper.writeValueAsString(body.get("recommended_actions")));
            report.setTopTerms(objectMapper.writeValueAsString(body.get("top_terms")));
            report.setExplanation(objectMapper.writeValueAsString(body.get("explanation")));
        } catch (Exception e) {
            // Silently maintain existing string fields if serialization encounters an issue
        }

        // Persist to relational database
        SafetyReport saved = repository.save(report);
        return mapToResponse(saved, (String) body.get("decision_support_disclaimer"));
    }

    /**
     * Fetches all safety reports from database in reverse chronological order.
     */
    public List<SafetyReportResponse> getAllReports() {
        return repository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Filters for HIGH and CRITICAL risk reports for expedited safety intervention.
     */
    public List<SafetyReportResponse> getHighRiskReports() {
        return repository.findByRiskLevelInOrderByCreatedAtDesc(Arrays.asList("HIGH", "CRITICAL"))
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Aggregates real-time statistics for the management overview dashboard.
     * Computes totals, positive SIF counts, category distribution, and risk breakdown.
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

        // Compute precursor distribution using Java Streams grouping
        List<SafetyReport> all = repository.findAll();
        Map<String, Long> precDist = all.stream()
                .filter(r -> r.getPrecursorType() != null)
                .collect(Collectors.groupingBy(SafetyReport::getPrecursorType, Collectors.counting()));
        stats.setPrecursorDistribution(precDist);

        // Compute risk level breakdown
        Map<String, Long> riskDist = all.stream()
                .filter(r -> r.getRiskLevel() != null)
                .collect(Collectors.groupingBy(SafetyReport::getRiskLevel, Collectors.counting()));
        stats.setRiskDistribution(riskDist);

        return stats;
    }

    private SafetyReportResponse mapToResponse(SafetyReport report) {
        return mapToResponse(report, "Prototype Decision Support: Requires safety officer review.");
    }

    /**
     * Helper mapper that converts a JPA Entity to an API Response DTO
     * and deserializes internal JSON strings back into structured collections.
     */
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

        // Parse JSON strings back into strongly-typed Java Collections for JSON serialization
        try {
            if (report.getDetectedFactors() != null) {
                resp.setDetectedFactors(objectMapper.readValue(report.getDetectedFactors(), new TypeReference<List<String>>() {}));
            }
            if (report.getPotentialConsequences() != null) {
                resp.setPotentialConsequences(objectMapper.readValue(report.getPotentialConsequences(), new TypeReference<List<String>>() {}));
            }
            if (report.getRecommendedActions() != null) {
                resp.setRecommendedActions(objectMapper.readValue(report.getRecommendedActions(), new TypeReference<List<String>>() {}));
            }
            if (report.getTopTerms() != null) {
                resp.setTopTerms(objectMapper.readValue(report.getTopTerms(), new TypeReference<List<Map<String, Object>>>() {}));
            }
            if (report.getExplanation() != null) {
                resp.setExplanation(objectMapper.readValue(report.getExplanation(), new TypeReference<List<String>>() {}));
            }
        } catch (Exception e) {
            // Lists remain empty if JSON parsing fails
        }

        return resp;
    }

    /**
     * Pre-populates the in-memory database on application startup.
     * Ensures judges immediately see rich analytics and test data upon launching the dashboard.
     */
    @PostConstruct
    public void seedInitialDemoData() {
        if (repository.count() == 0) {
            List<AnalyzeReportRequest> seedCases = new ArrayList<>();

            // Case 1: Fall from Height
            AnalyzeReportRequest c1 = new AnalyzeReportRequest();
            c1.setReport("Worker fell 15 feet from an unsecured ladder without harness while painting exterior tank shell.");
            c1.setReportType("Unsafe Act / Condition");
            c1.setLocation("OIL Rig Alpha, Duliajan");
            seedCases.add(c1);

            // Case 2: Electrical Hazard
            AnalyzeReportRequest c2 = new AnalyzeReportRequest();
            c2.setReport("Electrician was working on an energized 480V breaker panel without lockout tagout or voltage testing.");
            c2.setReportType("Unsafe Act");
            c2.setLocation("Gas Compression Plant 2, Moran");
            seedCases.add(c2);

            // Case 3: Confined Space & Toxic Atmosphere
            AnalyzeReportRequest c3 = new AnalyzeReportRequest();
            c3.setReport("Two workers entered crude oil storage tank for cleaning without atmospheric gas testing or ventilation.");
            c3.setReportType("Unsafe Condition");
            c3.setLocation("Crude Tank Farm, Digboi");
            seedCases.add(c3);

            // Case 4: Minor Housekeeping (Low Risk Control)
            AnalyzeReportRequest c4 = new AnalyzeReportRequest();
            c4.setReport("Trash and empty cardboard boxes left in hallway near office doorway obstructing walkway.");
            c4.setReportType("Near Miss");
            c4.setLocation("Administrative Building, Guwahati");
            seedCases.add(c4);

            // Case 5: Vehicle & Pedestrian Interaction
            AnalyzeReportRequest c5 = new AnalyzeReportRequest();
            c5.setReport("Forklift operator was driving in reverse with obstructed view and nearly collided with a pedestrian worker.");
            c5.setReportType("Near Miss");
            c5.setLocation("Central Warehouse, Duliajan");
            seedCases.add(c5);

            for (AnalyzeReportRequest req : seedCases) {
                try {
                    analyzeAndSave(req);
                } catch (Exception e) {
                    System.err.println("Seed notice (AI service might still be warming up): " + e.getMessage());
                }
            }
        }
    }
}

