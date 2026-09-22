package com.sih.sif.controller;

import com.sih.sif.dto.AnalyzeReportRequest;
import com.sih.sif.dto.DashboardStatsResponse;
import com.sih.sif.dto.SafetyReportResponse;
import com.sih.sif.service.SafetyReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API Controller for Safety Report Operations.
 * 
 * Responsibilities:
 * - Serves as the primary HTTP entry point for the React frontend.
 * - Handles report submission, triggers AI analysis, and persists incidents.
 * - Exposes statistical endpoints for real-time dashboard analytics.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allows cross-origin requests from the React client on port 5173
public class SafetyReportController {

    private final SafetyReportService service;

    /**
     * Constructor-based dependency injection for SafetyReportService.
     * Spring automatically provides the service singleton instance.
     */
    public SafetyReportController(SafetyReportService service) {
        this.service = service;
    }

    /**
     * Endpoint: POST /api/reports/analyze
     * Receives a new safety report description from the user, validates it,
     * forwards it to the Python NLP engine for inference, saves the result,
     * and returns the comprehensive prediction breakdown.
     */
    @PostMapping("/reports/analyze")
    public ResponseEntity<SafetyReportResponse> analyzeReport(@RequestBody AnalyzeReportRequest request) {
        // Basic input validation: ensure narrative text is not null or blank
        if (request.getReport() == null || request.getReport().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(service.analyzeAndSave(request));
    }

    /**
     * Endpoint: GET /api/reports
     * Retrieves all historically logged safety reports ordered from newest to oldest.
     */
    @GetMapping("/reports")
    public ResponseEntity<List<SafetyReportResponse>> getAllReports() {
        return ResponseEntity.ok(service.getAllReports());
    }

    /**
     * Endpoint: GET /api/reports/high-risk
     * Retrieves only reports that have been classified as HIGH or CRITICAL risk.
     */
    @GetMapping("/reports/high-risk")
    public ResponseEntity<List<SafetyReportResponse>> getHighRiskReports() {
        return ResponseEntity.ok(service.getHighRiskReports());
    }

    /**
     * Endpoint: GET /api/dashboard/stats
     * Computes real-time operational metrics across all logged reports, including
     * total counts, SIF precursor detections, category breakdowns, and risk distributions.
     */
    @GetMapping("/dashboard/stats")
    public ResponseEntity<DashboardStatsResponse> getDashboardStats() {
        return ResponseEntity.ok(service.getDashboardStats());
    }
}

