package com.sih.sif.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * AnalyzeReportRequest.java
 *
 * Data Transfer Object (DTO) capturing the JSON payload sent by the frontend client
 * when submitting an incident or near-miss report for NLP analysis.
 */
public class AnalyzeReportRequest {

    @NotBlank(message = "Safety report description cannot be blank")
    @Size(max = 5000, message = "Safety report description must not exceed 5000 characters")
    private String report;

    @Pattern(
        regexp = "^(Unsafe Act|Unsafe Condition|Near Miss|Incident Observation|Unsafe Act / Condition)$",
        message = "Report type must be one of: Unsafe Act, Unsafe Condition, Near Miss, Incident Observation, Unsafe Act / Condition"
    )
    private String reportType = "Unsafe Act";

    @Size(max = 200, message = "Location must not exceed 200 characters")
    private String location;

    public AnalyzeReportRequest() {}

    public String getReport() { return report; }
    public void setReport(String report) { this.report = report; }

    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
