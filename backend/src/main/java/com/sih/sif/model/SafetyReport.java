package com.sih.sif.model;

import jakarta.persistence.*;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

/**
 * SafetyReport.java
 *
 * Entity representing a safety incident or observation report.
 * Supports both relational JPA tables (H2) and MongoDB collections (Mongo Atlas).
 */
@Entity
@Table(name = "safety_reports")
@Document(collection = "safety_reports")
public class SafetyReport {

    /**
     * Unique identifier for each safety report record.
     * Mapped for both JPA (auto-increment IDENTITY) and MongoDB (@org.springframework.data.annotation.Id).
     */
    @Id
    @org.springframework.data.annotation.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The original verbatim text of the safety incident, near-miss, or observation.
     * Mapped as columnDefinition = "TEXT" so it can accommodate longer narratives without 255-char truncation.
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String reportText;

    // Type of report (e.g., "Near Miss", "Unsafe Act", "Unsafe Condition")
    private String reportType;

    // Physical site or work area (e.g., "Drilling Rig #4", "Wellhead Platform B")
    private String location;

    // AI Classification Output: True if report contains High-Energy/SIF Precursor indicators
    private Boolean sifPrecursorDetected;

    // Specific category identified (e.g., "Suspended Load", "Toxic Gas Exposure", "Pressure Hazard")
    private String precursorType;

    // Calibrated numeric risk score on a 0 - 100 scale
    private Double riskScore;

    // Categorical risk bucket: "CRITICAL", "HIGH", "MEDIUM", or "LOW"
    private String riskLevel;

    // Model confidence percentage (e.g., 0.88 = 88% confidence)
    private Double confidence;

    // JSON-serialized list of detected hazard factors / context indicators
    @Column(columnDefinition = "TEXT")
    private String detectedFactors;

    // JSON-serialized list of potential consequences (e.g., "Crush Injury", "Asphyxiation")
    @Column(columnDefinition = "TEXT")
    private String potentialConsequences;

    // JSON-serialized list of safety recommendations / hierarchy of controls
    @Column(columnDefinition = "TEXT")
    private String recommendedActions;

    // JSON-serialized list of top N-gram feature terms contributing to the prediction
    @Column(columnDefinition = "TEXT")
    private String topTerms;

    // High-level NLP rationale / explanation generated for safety officers
    @Column(columnDefinition = "TEXT")
    private String explanation;

    // Timestamp when the record was received and saved
    private LocalDateTime createdAt;

    /**
     * JPA Lifecycle Hook: automatically executes right before an entity is first persisted
     * into the database table, stamping it with the current date & time if not already set.
     */
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Default no-args constructor required by JPA/Hibernate reflection
    public SafetyReport() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getReportText() { return reportText; }
    public void setReportText(String reportText) { this.reportText = reportText; }

    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Boolean getSifPrecursorDetected() { return sifPrecursorDetected; }
    public void setSifPrecursorDetected(Boolean sifPrecursorDetected) { this.sifPrecursorDetected = sifPrecursorDetected; }

    public String getPrecursorType() { return precursorType; }
    public void setPrecursorType(String precursorType) { this.precursorType = precursorType; }

    public Double getRiskScore() { return riskScore; }
    public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public String getDetectedFactors() { return detectedFactors; }
    public void setDetectedFactors(String detectedFactors) { this.detectedFactors = detectedFactors; }

    public String getPotentialConsequences() { return potentialConsequences; }
    public void setPotentialConsequences(String potentialConsequences) { this.potentialConsequences = potentialConsequences; }

    public String getRecommendedActions() { return recommendedActions; }
    public void setRecommendedActions(String recommendedActions) { this.recommendedActions = recommendedActions; }

    public String getTopTerms() { return topTerms; }
    public void setTopTerms(String topTerms) { this.topTerms = topTerms; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
