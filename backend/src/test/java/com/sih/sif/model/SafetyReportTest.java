package com.sih.sif.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SafetyReportTest {

    @Test
    void defaultConstructorInitializesCreatedAt() {
        SafetyReport report = new SafetyReport();
        assertNotNull(report.getCreatedAt());
    }

    @Test
    void canSetAndGetCreatedAt() {
        SafetyReport report = new SafetyReport();
        LocalDateTime createdAt = LocalDateTime.of(2025, 1, 2, 3, 4);
        report.setCreatedAt(createdAt);

        assertEquals(createdAt, report.getCreatedAt());
    }

    @Test
    void accessorsRoundTripEntityState() {
        SafetyReport report = new SafetyReport();
        report.setId("rep-9");
        report.setReportText("Text");
        report.setReportType("Near Miss");
        report.setLocation("Site");
        report.setSifPrecursorDetected(true);
        report.setPrecursorType("Fall");
        report.setRiskScore(88.0);
        report.setRiskLevel("CRITICAL");
        report.setConfidence(0.9);
        report.setDetectedFactors(List.of("factor1"));
        report.setPotentialConsequences(List.of("consequence1"));
        report.setRecommendedActions(List.of("action1"));
        report.setTopTerms(List.of(Map.of("term", "hazard")));
        report.setExplanation(List.of("explanation1"));

        assertEquals("rep-9", report.getId());
        assertEquals("Text", report.getReportText());
        assertEquals("Near Miss", report.getReportType());
        assertEquals("Site", report.getLocation());
        assertTrue(report.getSifPrecursorDetected());
        assertEquals("Fall", report.getPrecursorType());
        assertEquals(88.0, report.getRiskScore());
        assertEquals("CRITICAL", report.getRiskLevel());
        assertEquals(0.9, report.getConfidence());
        assertEquals(List.of("factor1"), report.getDetectedFactors());
        assertEquals(List.of("consequence1"), report.getPotentialConsequences());
        assertEquals(List.of("action1"), report.getRecommendedActions());
        assertEquals(List.of(Map.of("term", "hazard")), report.getTopTerms());
        assertEquals(List.of("explanation1"), report.getExplanation());
    }
}