package com.ghasl_service.demo.controller;

import com.ghasl_service.demo.dto.MarketingROIResponse;
import com.ghasl_service.demo.dto.MarketingSpendUpdateRequest;
import com.ghasl_service.demo.service.MarketingReportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for marketing report endpoints.
 * Provides marketing ROI metrics for the gauge chart.
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
public class MarketingReportController {

    private final MarketingReportService marketingReportService;

    public MarketingReportController(MarketingReportService marketingReportService) {
        this.marketingReportService = marketingReportService;
    }

    /**
     * GET /api/v1/admin/reports/marketing-roi
     * Returns marketing ROI metrics for the gauge chart.
     * 
     * Query Parameters:
     * - period: "today", "week", or "month" (default: "month")
     * 
     * Response:
     * - monthlyAdSpend: Total marketing spend for the period
     * - newCustomers: Count of first-time customers in the period
     * - customerAcquisitionCost: CAC = Ad Spend / New Customers
     * - averageCustomerLifetimeValue: System-wide average CLV
     * - roiRatio: CLV / CAC
     * - currency: "IQD"
     * - period: YearMonth of the report
     */
    @GetMapping("/marketing-roi")
    public MarketingROIResponse getMarketingROI(
            @RequestParam(defaultValue = "month") String period) {
        return marketingReportService.getMarketingROI(period);
    }

    /**
     * POST /api/v1/admin/reports/marketing-spend
     * Updates marketing spend for a specific period.
     * 
     * Request Body:
     * - amount: The new ad spend amount
     * - period: "today", "week", or "month"
     * 
     * Response: 200 OK on success
     */
    @PostMapping("/marketing-spend")
    public ResponseEntity<Void> updateMarketingSpend(@Valid @RequestBody MarketingSpendUpdateRequest request) {
        marketingReportService.updateMarketingSpend(request);
        return ResponseEntity.ok().build();
    }
}
