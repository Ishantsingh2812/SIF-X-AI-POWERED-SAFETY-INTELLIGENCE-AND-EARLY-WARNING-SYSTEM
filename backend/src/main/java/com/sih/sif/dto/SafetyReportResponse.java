package com.sih.sif.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SafetyReportResponse.java
 *
 * Data Transfer Object (DTO) containing the full analysis and persistence response
 * returned to the client frontend.
 *
 * It unites:
 * 1. Database metadata (id, createdAt)
 * 2. Original submission fields (reportText, location, reportType)
 * 3. Machine Learning predictions (sifPrecursorDetected, precursorType, confidence, riskScore, riskLevel)
 * 4. Human-readable explainability artifacts (detectedFactors, potentialConsequences, recommendedActions, topTerms, explanation)
 * 5. Mandatory AI Decision Support disclaimer emphasizing human oversight
 */
public class SafetyReportResponse {
    private String id;
    private String reportText;
    private String reportType;
    private String location;

    // AI Classification details
    private Boolean sifPrecursorDetected;
    private String precursorType;
    private Double riskScore;
    private String riskLevel;
    private Double confidence;

    // Explainable AI factors & contextual safety insights
    private List<String> detectedFactors;
    private List<String> potentialConsequences;
    private List<String> recommendedActions;
    private List<Map<String, Object>> topTerms;
    private List<String> explanation;

    // Metadata
    private LocalDateTime createdAt;

    // Human-in-the-loop decision support disclaimer
    private String decisionSupportDisclaimer;

    public SafetyReportResponse() {}

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

    public String getDecisionSupportDisclaimer() { return decisionSupportDisclaimer; }
    public void setDecisionSupportDisclaimer(String decisionSupportDisclaimer) { this.decisionSupportDisclaimer = decisionSupportDisclaimer; }
}
