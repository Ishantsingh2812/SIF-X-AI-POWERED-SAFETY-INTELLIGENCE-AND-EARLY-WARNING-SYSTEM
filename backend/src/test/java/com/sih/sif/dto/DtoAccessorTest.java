package com.sih.sif.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DtoAccessorTest {

    @Test
    void analyzeReportRequestAccessorsRoundTripValues() {
        AnalyzeReportRequest request = new AnalyzeReportRequest();
        request.setReport("Report");
        request.setReportType("Near Miss");
        request.setLocation("Site");

        assertEquals("Report", request.getReport());
        assertEquals("Near Miss", request.getReportType());
        assertEquals("Site", request.getLocation());
    }

    @Test
    void safetyReportResponseAccessorsRoundTripValues() {
        SafetyReportResponse response = new SafetyReportResponse();
        LocalDateTime createdAt = LocalDateTime.of(2025, 1, 2, 3, 4);
        response.setId(1L);
        response.setReportText("Report");
        response.setReportType("Near Miss");
        response.setLocation("Site");
        response.setSifPrecursorDetected(true);
        response.setPrecursorType("Fall");
        response.setRiskScore(80.0);
        response.setRiskLevel("HIGH");
        response.setConfidence(0.8);
        response.setDetectedFactors(List.of("Ladder"));
        response.setPotentialConsequences(List.of("Injury"));
        response.setRecommendedActions(List.of("Inspect"));
        response.setTopTerms(List.of(Map.of("term", "ladder")));
        response.setExplanation(List.of("Unsafe access"));
        response.setCreatedAt(createdAt);
        response.setDecisionSupportDisclaimer("Review");

        assertEquals(1L, response.getId());
        assertEquals("Report", response.getReportText());
        assertEquals("Near Miss", response.getReportType());
        assertEquals("Site", response.getLocation());
        assertTrue(response.getSifPrecursorDetected());
        assertEquals("Fall", response.getPrecursorType());
        assertEquals(80.0, response.getRiskScore());
        assertEquals("HIGH", response.getRiskLevel());
        assertEquals(0.8, response.getConfidence());
        assertEquals(List.of("Ladder"), response.getDetectedFactors());
        assertEquals(List.of("Injury"), response.getPotentialConsequences());
        assertEquals(List.of("Inspect"), response.getRecommendedActions());
        assertEquals(List.of(Map.of("term", "ladder")), response.getTopTerms());
        assertEquals(List.of("Unsafe access"), response.getExplanation());
        assertEquals(createdAt, response.getCreatedAt());
        assertEquals("Review", response.getDecisionSupportDisclaimer());
    }

    @Test
    void dashboardStatsResponseAccessorsRoundTripValues() {
        DashboardStatsResponse stats = new DashboardStatsResponse();
        stats.setTotalReports(5);
        stats.setSifPrecursors(3);
        stats.setHighRiskReports(2);
        stats.setCriticalReports(1);
        stats.setPrecursorDistribution(Map.of("Fall", 2L));
        stats.setRiskDistribution(Map.of("HIGH", 2L));

        assertEquals(5, stats.getTotalReports());
        assertEquals(3, stats.getSifPrecursors());
        assertEquals(2, stats.getHighRiskReports());
        assertEquals(1, stats.getCriticalReports());
        assertEquals(Map.of("Fall", 2L), stats.getPrecursorDistribution());
        assertEquals(Map.of("HIGH", 2L), stats.getRiskDistribution());
    }
}