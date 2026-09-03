package com.ghasl_service.demo.controller;

import com.ghasl_service.demo.dto.ParetoAnalysisResponse;
import com.ghasl_service.demo.dto.ReportGenerationRequest;
import com.ghasl_service.demo.dto.ReportGenerationResponse;
import com.ghasl_service.demo.service.ReportGenerationService;
import com.ghasl_service.demo.service.ReportService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportGenerationService reportGenerationService;

    public ReportController(ReportService reportService, ReportGenerationService reportGenerationService) {
        this.reportService = reportService;
        this.reportGenerationService = reportGenerationService;
    }

    /**
     * GET /api/v1/admin/reports/pareto-analysis?period={today|week|month}
     * Returns Pareto analysis data for service profit distribution (80/20 rule)
     */
    @GetMapping("/pareto-analysis")
    public ParetoAnalysisResponse getParetoAnalysis(@RequestParam(defaultValue = "month") String period) {
        return reportService.getParetoAnalysis(period);
    }

    /**
     * POST /api/v1/admin/reports/generate
     * Generates a report based on the requested type (DAILY, MONTHLY, YEARLY).
     * All financial calculations are performed in the backend (Zero Trust Architecture).
     */
    @PostMapping("/generate")
    public ReportGenerationResponse generateReport(@RequestBody ReportGenerationRequest request) {
        return reportGenerationService.generateReport(request.getReportType());
    }
}
