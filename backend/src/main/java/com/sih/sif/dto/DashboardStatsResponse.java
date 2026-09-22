package com.sih.sif.dto;

import java.util.Map;

/**
 * DashboardStatsResponse.java
 *
 * Data Transfer Object (DTO) returned by GET /api/dashboard/stats to feed
 * the real-time safety dashboard cards and charts in the React frontend.
 *
 * It aggregates incident counts, risk tiers, and precursor categories across
 * all saved reports in the database.
 */
public class DashboardStatsResponse {
    // Total count of reports stored in the database
    private long totalReports;

    // Total count of reports where a high-energy SIF precursor was confirmed
    private long sifPrecursors;

    // Reports classified into the HIGH risk tier (score >= 65 and < 85)
    private long highRiskReports;

    // Reports classified into the CRITICAL risk tier (score >= 85)
    private long criticalReports;

    // Distribution map of precursor counts by category (e.g., {"Suspended Load": 4, "Toxic Gas Exposure": 3})
    private Map<String, Long> precursorDistribution;

    // Distribution map of reports by risk tier (e.g., {"CRITICAL": 2, "HIGH": 5, "MEDIUM": 8, "LOW": 3})
    private Map<String, Long> riskDistribution;

    public DashboardStatsResponse() {}

    public long getTotalReports() { return totalReports; }
    public void setTotalReports(long totalReports) { this.totalReports = totalReports; }

    public long getSifPrecursors() { return sifPrecursors; }
    public void setSifPrecursors(long sifPrecursors) { this.sifPrecursors = sifPrecursors; }

    public long getHighRiskReports() { return highRiskReports; }
    public void setHighRiskReports(long highRiskReports) { this.highRiskReports = highRiskReports; }

    public long getCriticalReports() { return criticalReports; }
    public void setCriticalReports(long criticalReports) { this.criticalReports = criticalReports; }

    public Map<String, Long> getPrecursorDistribution() { return precursorDistribution; }
    public void setPrecursorDistribution(Map<String, Long> precursorDistribution) { this.precursorDistribution = precursorDistribution; }

    public Map<String, Long> getRiskDistribution() { return riskDistribution; }
    public void setRiskDistribution(Map<String, Long> riskDistribution) { this.riskDistribution = riskDistribution; }
}
