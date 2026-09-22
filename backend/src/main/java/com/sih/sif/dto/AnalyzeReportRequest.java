package com.sih.sif.dto;

import java.util.List;
import java.util.Map;

/**
 * AnalyzeReportRequest.java
 *
 * Data Transfer Object (DTO) capturing the JSON payload sent by the frontend client
 * when submitting an incident or near-miss report for NLP analysis.
 *
 * Spring Boot's Jackson ObjectMapper automatically deserializes incoming JSON keys
 * into these Java fields.
 */
public class AnalyzeReportRequest {
    // Verbatim text of the incident or safety observation
    private String report;

    // Optional category tag selected in the UI (e.g. "Near Miss", "Unsafe Act")
    private String reportType;

    // Worksite location or facility name (e.g. "Drilling Rig #4")
    private String location;

    public AnalyzeReportRequest() {}

    public String getReport() { return report; }
    public void setReport(String report) { this.report = report; }

    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
