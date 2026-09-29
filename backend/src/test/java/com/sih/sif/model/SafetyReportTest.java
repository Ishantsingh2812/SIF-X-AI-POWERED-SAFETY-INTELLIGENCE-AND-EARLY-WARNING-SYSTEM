package com.sih.sif.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SafetyReportTest {

    @Test
    void prePersistSetsCreatedAtWhenMissing() {
        SafetyReport report = new SafetyReport();

        report.prePersist();

        assertNotNull(report.getCreatedAt());
    }

    @Test
    void prePersistPreservesExistingCreatedAt() {
        SafetyReport report = new SafetyReport();
        LocalDateTime createdAt = LocalDateTime.of(2025, 1, 2, 3, 4);
        report.setCreatedAt(createdAt);

        report.prePersist();

        assertEquals(createdAt, report.getCreatedAt());
    }

    @Test
    void accessorsRoundTripEntityState() {
        SafetyReport report = new SafetyReport();
        report.setId(9L);
        report.setReportText("Text");
        report.setReportType("Near Miss");
        report.setLocation("Site");
        report.setSifPrecursorDetected(true);
        report.setPrecursorType("Fall");
        report.setRiskScore(88.0);
        report.setRiskLevel("CRITICAL");
        report.setConfidence(0.9);
        report.setDetectedFactors("[]");
        report.setPotentialConsequences("[]");
        report.setRecommendedActions("[]");
        report.setTopTerms("[]");
        report.setExplanation("[]");

        assertEquals(9L, report.getId());
        assertEquals("Text", report.getReportText());
        assertEquals("Near Miss", report.getReportType());
        assertEquals("Site", report.getLocation());
        assertTrue(report.getSifPrecursorDetected());
        assertEquals("Fall", report.getPrecursorType());
        assertEquals(88.0, report.getRiskScore());
        assertEquals("CRITICAL", report.getRiskLevel());
        assertEquals(0.9, report.getConfidence());
        assertEquals("[]", report.getDetectedFactors());
        assertEquals("[]", report.getPotentialConsequences());
        assertEquals("[]", report.getRecommendedActions());
        assertEquals("[]", report.getTopTerms());
        assertEquals("[]", report.getExplanation());
    }
}