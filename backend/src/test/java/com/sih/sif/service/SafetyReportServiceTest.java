package com.sih.sif.service;

import com.sih.sif.dto.AnalyzeReportRequest;
import com.sih.sif.dto.DashboardStatsResponse;
import com.sih.sif.dto.SafetyReportResponse;
import com.sih.sif.model.SafetyReport;
import com.sih.sif.repository.SafetyReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SafetyReportServiceTest {

    @Mock
    private SafetyReportRepository repository;

    @Mock
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    @Mock
    private RestTemplate restTemplate;

    private SafetyReportService service;

    @BeforeEach
    void setUp() {
        service = new SafetyReportService(repository, mongoTemplate, restTemplate);
        ReflectionTestUtils.setField(service, "aiServiceUrl", "http://127.0.0.1:8000");
        ReflectionTestUtils.setField(service, "seedDemoData", true);
    }

    @Test
    void analyzeAndSaveMapsAiResponseAndPersistsReport() {
        AnalyzeReportRequest request = request("Worker fell", "Near Miss", "Rig A");
        Map<String, Object> body = new HashMap<>();
        body.put("sif_precursor_detected", true);
        body.put("precursor_type", "Fall from Height");
        body.put("risk_score", 87);
        body.put("risk_level", "CRITICAL");
        body.put("confidence", 0.94);
        body.put("detected_factors", List.of("No harness"));
        body.put("potential_consequences", List.of("Serious injury"));
        body.put("recommended_actions", List.of("Install guardrails"));
        body.put("top_terms", List.of(Map.of("term", "harness", "weight", 0.8)));
        body.put("explanation", List.of("Unprotected elevation"));
        body.put("decision_support_disclaimer", "Review required");
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(body));
        when(repository.save(any(SafetyReport.class))).thenAnswer(invocation -> {
            SafetyReport report = invocation.getArgument(0);
            report.setId("rep-42");
            return report;
        });

        SafetyReportResponse response = service.analyzeAndSave(request);

        assertEquals("rep-42", response.getId());
        assertEquals("Worker fell", response.getReportText());
        assertTrue(response.getSifPrecursorDetected());
        assertEquals("CRITICAL", response.getRiskLevel());
        assertEquals(87.0, response.getRiskScore());
        assertEquals(List.of("No harness"), response.getDetectedFactors());
        assertEquals("Review required", response.getDecisionSupportDisclaimer());
        verify(restTemplate).postForEntity(eq("http://127.0.0.1:8000/predict"), any(), eq(Map.class));
        verify(repository).save(any(SafetyReport.class));
    }

    @Test
    void analyzeAndSaveUsesZeroForNonNumericScores() {
        Map<String, Object> body = Map.of(
                "sif_precursor_detected", false,
                "risk_score", "unknown",
                "confidence", "unknown");
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(body));
        when(repository.save(any(SafetyReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SafetyReportResponse response = service.analyzeAndSave(request("Minor spill", "Near Miss", "Office"));

        assertEquals(0.0, response.getRiskScore());
        assertEquals(0.0, response.getConfidence());
    }

    @Test
    void analyzeAndSaveRejectsEmptyAiResponse() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(null));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.analyzeAndSave(request("Report", "Type", "Location")));

        assertEquals("Empty response received from AI Service", exception.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void getAllReportsMapsReportsWithDefaultDisclaimer() {
        SafetyReport report = report("rep-1", "HIGH", "Fall", List.of("ladder"));
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(report));

        List<SafetyReportResponse> responses = service.getAllReports();

        assertEquals(1, responses.size());
        assertEquals("rep-1", responses.get(0).getId());
        assertEquals(List.of("ladder"), responses.get(0).getDetectedFactors());
        assertEquals("Prototype Decision Support: Requires safety officer review.",
                responses.get(0).getDecisionSupportDisclaimer());
    }

    @Test
    void getHighRiskReportsQueriesHighAndCriticalLevels() {
        when(repository.findByRiskLevelInOrderByCreatedAtDesc(List.of("HIGH", "CRITICAL")))
                .thenReturn(List.of(report("rep-2", "CRITICAL", "Toxic Gas", null)));

        List<SafetyReportResponse> responses = service.getHighRiskReports();

        assertEquals(1, responses.size());
        assertEquals("CRITICAL", responses.get(0).getRiskLevel());
        verify(repository).findByRiskLevelInOrderByCreatedAtDesc(List.of("HIGH", "CRITICAL"));
    }

    @Test
    void getDashboardStatsAggregatesCountsAndDistributions() {
        when(repository.count()).thenReturn(3L);
        when(repository.countBySifPrecursorDetectedTrue()).thenReturn(2L);
        when(repository.countByRiskLevel("HIGH")).thenReturn(2L);
        when(repository.countByRiskLevel("CRITICAL")).thenReturn(1L);

        org.bson.Document precDoc1 = new org.bson.Document("_id", "Fall").append("count", 2L);
        org.bson.Document precDoc2 = new org.bson.Document("_id", "Gas").append("count", 1L);
        org.springframework.data.mongodb.core.aggregation.AggregationResults<org.bson.Document> precResults =
                new org.springframework.data.mongodb.core.aggregation.AggregationResults<>(List.of(precDoc1, precDoc2), new org.bson.Document());

        org.bson.Document riskDoc1 = new org.bson.Document("_id", "HIGH").append("count", 2L);
        org.bson.Document riskDoc2 = new org.bson.Document("_id", "CRITICAL").append("count", 1L);
        org.springframework.data.mongodb.core.aggregation.AggregationResults<org.bson.Document> riskResults =
                new org.springframework.data.mongodb.core.aggregation.AggregationResults<>(List.of(riskDoc1, riskDoc2), new org.bson.Document());

        when(mongoTemplate.aggregate(any(org.springframework.data.mongodb.core.aggregation.Aggregation.class), eq("safety_reports"), eq(org.bson.Document.class)))
                .thenReturn(precResults)
                .thenReturn(riskResults);

        DashboardStatsResponse stats = service.getDashboardStats();

        assertEquals(3L, stats.getTotalReports());
        assertEquals(2L, stats.getSifPrecursors());
        assertEquals(Map.of("Fall", 2L, "Gas", 1L), stats.getPrecursorDistribution());
        assertEquals(Map.of("HIGH", 2L, "CRITICAL", 1L), stats.getRiskDistribution());
    }

    @Test
    void seedInitialDemoDataCreatesFiveCasesWhenRepositoryIsEmpty() {
        when(repository.count()).thenReturn(0L);
        SafetyReportService spyService = spy(service);
        doReturn(new SafetyReportResponse()).when(spyService).analyzeAndSave(any(AnalyzeReportRequest.class));

        spyService.seedInitialDemoData();

        verify(spyService, times(5)).analyzeAndSave(any(AnalyzeReportRequest.class));
    }

    @Test
    void seedInitialDemoDataSkipsExistingData() {
        when(repository.count()).thenReturn(1L);

        service.seedInitialDemoData();

        verify(repository).count();
        verifyNoInteractions(restTemplate);
    }

    private AnalyzeReportRequest request(String report, String type, String location) {
        AnalyzeReportRequest request = new AnalyzeReportRequest();
        request.setReport(report);
        request.setReportType(type);
        request.setLocation(location);
        return request;
    }

    private SafetyReport report(String id, String riskLevel, String precursorType, List<String> detectedFactors) {
        SafetyReport report = new SafetyReport();
        report.setId(id);
        report.setReportText("Report " + id);
        report.setRiskLevel(riskLevel);
        report.setPrecursorType(precursorType);
        report.setDetectedFactors(detectedFactors != null ? detectedFactors : List.of());
        report.setCreatedAt(LocalDateTime.now());
        return report;
    }
}