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
    private RestTemplate restTemplate;

    private SafetyReportService service;

    @BeforeEach
    void setUp() {
        service = new SafetyReportService(repository);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "aiServiceUrl", "http://127.0.0.1:8000");
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
            report.setId(42L);
            return report;
        });

        SafetyReportResponse response = service.analyzeAndSave(request);

        assertEquals(42L, response.getId());
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

        assertEquals("Empty response received from Python AI Service", exception.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void getAllReportsMapsReportsWithDefaultDisclaimer() {
        SafetyReport report = report(1L, "HIGH", "Fall", "[\"ladder\"]");
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(report));

        List<SafetyReportResponse> responses = service.getAllReports();

        assertEquals(1, responses.size());
        assertEquals(1L, responses.get(0).getId());
        assertEquals(List.of("ladder"), responses.get(0).getDetectedFactors());
        assertEquals("Prototype Decision Support: Requires safety officer review.",
                responses.get(0).getDecisionSupportDisclaimer());
    }

    @Test
    void getHighRiskReportsQueriesHighAndCriticalLevels() {
        when(repository.findByRiskLevelInOrderByCreatedAtDesc(List.of("HIGH", "CRITICAL")))
                .thenReturn(List.of(report(2L, "CRITICAL", "Toxic Gas", null)));

        List<SafetyReportResponse> responses = service.getHighRiskReports();

        assertEquals(1, responses.size());
        assertEquals("CRITICAL", responses.get(0).getRiskLevel());
        verify(repository).findByRiskLevelInOrderByCreatedAtDesc(List.of("HIGH", "CRITICAL"));
    }

    @Test
    void getDashboardStatsAggregatesCountsAndDistributions() {
        SafetyReport first = report(1L, "HIGH", "Fall", null);
        first.setSifPrecursorDetected(true);
        SafetyReport second = report(2L, "HIGH", "Fall", null);
        second.setSifPrecursorDetected(false);
        SafetyReport third = report(3L, "CRITICAL", "Gas", null);
        third.setSifPrecursorDetected(true);
        when(repository.count()).thenReturn(3L);
        when(repository.countBySifPrecursorDetectedTrue()).thenReturn(2L);
        when(repository.countByRiskLevel("HIGH")).thenReturn(2L);
        when(repository.countByRiskLevel("CRITICAL")).thenReturn(1L);
        when(repository.findAll()).thenReturn(List.of(first, second, third));

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

    private SafetyReport report(Long id, String riskLevel, String precursorType, String detectedFactors) {
        SafetyReport report = new SafetyReport();
        report.setId(id);
        report.setReportText("Report " + id);
        report.setRiskLevel(riskLevel);
        report.setPrecursorType(precursorType);
        report.setDetectedFactors(detectedFactors);
        report.setCreatedAt(LocalDateTime.now());
        return report;
    }
}