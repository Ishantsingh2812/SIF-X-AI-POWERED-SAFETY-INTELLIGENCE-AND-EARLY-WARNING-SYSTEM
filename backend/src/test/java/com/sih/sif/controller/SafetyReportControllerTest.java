package com.sih.sif.controller;

import com.sih.sif.dto.AnalyzeReportRequest;
import com.sih.sif.dto.DashboardStatsResponse;
import com.sih.sif.dto.SafetyReportResponse;
import com.sih.sif.service.SafetyReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SafetyReportControllerTest {

    @Mock
    private SafetyReportService service;

    @InjectMocks
    private SafetyReportController controller;

    @Test
    void analyzeReportRejectsNullOrBlankNarrative() {
        AnalyzeReportRequest request = new AnalyzeReportRequest();

        ResponseEntity<SafetyReportResponse> nullResponse = controller.analyzeReport(request);
        request.setReport("  ");
        ResponseEntity<SafetyReportResponse> blankResponse = controller.analyzeReport(request);

        assertEquals(HttpStatus.BAD_REQUEST, nullResponse.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, blankResponse.getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    void analyzeReportDelegatesValidRequest() {
        AnalyzeReportRequest request = new AnalyzeReportRequest();
        request.setReport("Unsafe ladder");
        SafetyReportResponse expected = new SafetyReportResponse();
        when(service.analyzeAndSave(request)).thenReturn(expected);

        ResponseEntity<SafetyReportResponse> response = controller.analyzeReport(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
        verify(service).analyzeAndSave(request);
    }

    @Test
    void readEndpointsReturnServiceResults() {
        List<SafetyReportResponse> reports = List.of(new SafetyReportResponse());
        DashboardStatsResponse stats = new DashboardStatsResponse();
        when(service.getAllReports()).thenReturn(reports);
        when(service.getHighRiskReports()).thenReturn(reports);
        when(service.getDashboardStats()).thenReturn(stats);

        assertSame(reports, controller.getAllReports().getBody());
        assertSame(reports, controller.getHighRiskReports().getBody());
        assertSame(stats, controller.getDashboardStats().getBody());
        verify(service).getAllReports();
        verify(service).getHighRiskReports();
        verify(service).getDashboardStats();
    }
}