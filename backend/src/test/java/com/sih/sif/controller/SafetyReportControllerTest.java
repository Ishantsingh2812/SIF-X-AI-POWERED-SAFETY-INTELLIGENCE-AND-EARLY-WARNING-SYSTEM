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
        com.sih.sif.dto.PagedResponse<SafetyReportResponse> pagedResponse =
                new com.sih.sif.dto.PagedResponse<>(List.of(new SafetyReportResponse()), 0, 20, 1, 1);
        DashboardStatsResponse stats = new DashboardStatsResponse();
        when(service.getReportsPaged(0, 20)).thenReturn(pagedResponse);
        when(service.getHighRiskReportsPaged(0, 20)).thenReturn(pagedResponse);
        when(service.getDashboardStats()).thenReturn(stats);

        assertSame(pagedResponse, controller.getAllReports(0, 20).getBody());
        assertSame(pagedResponse, controller.getHighRiskReports(0, 20).getBody());
        assertSame(stats, controller.getDashboardStats().getBody());
        verify(service).getReportsPaged(0, 20);
        verify(service).getHighRiskReportsPaged(0, 20);
        verify(service).getDashboardStats();
    }
}