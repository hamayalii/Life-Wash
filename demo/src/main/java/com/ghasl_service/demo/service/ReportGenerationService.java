package com.ghasl_service.demo.service;

import com.ghasl_service.demo.dto.ReportGenerationRequest;
import com.ghasl_service.demo.dto.ReportGenerationResponse;
import com.ghasl_service.demo.model.Order;
import com.ghasl_service.demo.repository.CustomerValueRepository;
import com.ghasl_service.demo.repository.OrderRepository;
import com.ghasl_service.demo.repository.ServiceCategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for generating daily, monthly, and yearly reports.
 * DELEGATES to ReportService for SSOT (Single Source of Truth) compliance.
 * All financial aggregations are performed in the backend (Zero Trust Architecture).
 */
@Service
public class ReportGenerationService {
    
    private static final Logger log = LoggerFactory.getLogger(ReportGenerationService.class);
    
    private final OrderRepository orderRepository;
    private final CustomerValueRepository customerValueRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final ReportService reportService; // SSOT for report aggregation
    
    public ReportGenerationService(OrderRepository orderRepository, 
                                  CustomerValueRepository customerValueRepository,
                                  ServiceCategoryRepository serviceCategoryRepository,
                                  ReportService reportService) {
        this.orderRepository = orderRepository;
        this.customerValueRepository = customerValueRepository;
        this.serviceCategoryRepository = serviceCategoryRepository;
        this.reportService = reportService;
    }
    
    /**
     * Generates a report based on the requested type (Daily, Monthly, Yearly).
     * DELEGATES to ReportService for SSOT compliance.
     * All calculations are performed in the backend (Zero Trust Architecture).
     * Data flows as typed objects (ReportMetricsDTO), string formatting is terminal.
     */
    public ReportGenerationResponse generateReport(ReportGenerationRequest.ReportType reportType) {
        log.info("Generating {} report (delegating to ReportService for SSOT)", reportType);
        
        LocalDateTime now = LocalDateTime.now();
        com.ghasl_service.demo.dto.ReportMetricsDTO metrics;
        String title;
        
        // Get typed metrics from ReportService (SSOT)
        switch (reportType) {
            case DAILY:
                metrics = getDailyMetrics();
                title = "ڕاپۆرتی ڕۆژانە — " + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                break;
            case MONTHLY:
                metrics = getMonthlyMetrics();
                YearMonth currentMonth = YearMonth.now();
                String monthName = currentMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase();
                title = "ڕاپۆرتی مانگانە — " + monthName + " " + currentMonth.getYear();
                break;
            case YEARLY:
                metrics = getYearlyMetrics();
                title = "ڕاپۆرتی ساڵانە — " + LocalDate.now().getYear();
                break;
            default:
                throw new IllegalArgumentException("Unknown report type: " + reportType);
        }
        
        // Format DTO to Kurdish text (terminal operation)
        String reportText = reportService.formatReportAsKurdishText(title, metrics);
        
        // Build response from typed DTO (NO string parsing)
        ReportGenerationResponse response = new ReportGenerationResponse();
        response.setReportType(reportType);
        response.setPeriodLabel(title);
        response.setGeneratedAt(now);
        response.setTotalRevenue(metrics.totalRevenue());
        response.setTotalOrders(metrics.totalOrders() != null ? metrics.totalOrders().intValue() : 0);
        response.setAcceptedOrders(metrics.acceptedOrders() != null ? metrics.acceptedOrders().intValue() : 0);
        response.setRejectedOrders(metrics.rejectedOrders() != null ? metrics.rejectedOrders().intValue() : 0);
        response.setPendingOrders(metrics.pendingOrders() != null ? metrics.pendingOrders().intValue() : 0);
        response.setReportText(reportText);
        
        log.info("Report generated successfully via ReportService SSOT (typed DTO)");
        
        return response;
    }
    
    /**
     * Get daily metrics from ReportService
     */
    private com.ghasl_service.demo.dto.ReportMetricsDTO getDailyMetrics() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        
        LocalDate yesterday = today.minusDays(1);
        LocalDateTime prevStart = yesterday.atStartOfDay();
        LocalDateTime prevEnd = yesterday.plusDays(1).atStartOfDay();
        
        return reportService.buildReportMetrics(start, end, prevStart, prevEnd);
    }
    
    /**
     * Get monthly metrics from ReportService
     */
    private com.ghasl_service.demo.dto.ReportMetricsDTO getMonthlyMetrics() {
        YearMonth currentMonth = YearMonth.now();
        LocalDateTime start = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime end = currentMonth.plusMonths(1).atDay(1).atStartOfDay();
        
        YearMonth prevMonth = currentMonth.minusMonths(1);
        LocalDateTime prevStart = prevMonth.atDay(1).atStartOfDay();
        LocalDateTime prevEnd = prevMonth.plusMonths(1).atDay(1).atStartOfDay();
        
        return reportService.buildReportMetrics(start, end, prevStart, prevEnd);
    }
    
    /**
     * Get yearly metrics from ReportService
     */
    private com.ghasl_service.demo.dto.ReportMetricsDTO getYearlyMetrics() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.withDayOfYear(1).atStartOfDay();
        LocalDateTime end = today.plusYears(1).withDayOfYear(1).atStartOfDay();
        
        LocalDateTime prevStart = today.minusYears(1).withDayOfYear(1).atStartOfDay();
        LocalDateTime prevEnd = today.withDayOfYear(1).atStartOfDay();
        
        return reportService.buildReportMetrics(start, end, prevStart, prevEnd);
    }
}
