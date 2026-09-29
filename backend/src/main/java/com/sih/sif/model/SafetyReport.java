package com.sih.sif.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

/**
 * SafetyReport.java
 *
 * MongoDB Document representing a safety incident or observation report
 * stored in MongoDB Atlas in the 'safety_reports' collection.
 */
@Document(collection = "safety_reports")
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

    private String detectedFactors;
    private String potentialConsequences;
    private String recommendedActions;
    private String topTerms;
    private String explanation;

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
