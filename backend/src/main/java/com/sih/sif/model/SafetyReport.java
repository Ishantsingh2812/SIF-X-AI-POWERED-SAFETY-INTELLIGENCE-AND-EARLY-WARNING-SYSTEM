package com.sih.sif.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SafetyReport.java
 *
 * MongoDB Document representing a safety incident or observation report
 * stored in MongoDB Atlas in the 'safety_reports' collection.
 * Stores list fields natively without JSON stringification.
 */
@Document(collection = "safety_reports")
@CompoundIndex(name = "idx_created_risk", def = "{'createdAt': -1, 'riskLevel': 1}")
public class SafetyReport {

    /**
     * Unique identifier for each safety report record.
     * Mapped for MongoDB.
     */
    @Id
    private String id;

    private String reportText;
    private String reportType;
    private String location;

    private Boolean sifPrecursorDetected;
    private String precursorType;
    private Double riskScore;
    private String riskLevel;
    private Double confidence;

    private List<String> detectedFactors = new ArrayList<>();
    private List<String> potentialConsequences = new ArrayList<>();
    private List<String> recommendedActions = new ArrayList<>();
    private List<Map<String, Object>> topTerms = new ArrayList<>();
    private List<String> explanation = new ArrayList<>();

    private LocalDateTime createdAt = LocalDateTime.now();

    public SafetyReport() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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

    public List<String> getDetectedFactors() { return detectedFactors; }
    public void setDetectedFactors(List<String> detectedFactors) { this.detectedFactors = detectedFactors; }

    public List<String> getPotentialConsequences() { return potentialConsequences; }
    public void setPotentialConsequences(List<String> potentialConsequences) { this.potentialConsequences = potentialConsequences; }

    public List<String> getRecommendedActions() { return recommendedActions; }
    public void setRecommendedActions(List<String> recommendedActions) { this.recommendedActions = recommendedActions; }

    public List<Map<String, Object>> getTopTerms() { return topTerms; }
    public void setTopTerms(List<Map<String, Object>> topTerms) { this.topTerms = topTerms; }

    public List<String> getExplanation() { return explanation; }
    public void setExplanation(List<String> explanation) { this.explanation = explanation; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
