package com.ghasl_service.demo.service;

import com.ghasl_service.demo.dto.*;
import com.ghasl_service.demo.model.MeasurementUnit;
import com.ghasl_service.demo.repository.LeadRepository;
import com.ghasl_service.demo.repository.OrderRepository;
import com.ghasl_service.demo.repository.ServiceCategoryRepository;
import com.ghasl_service.demo.repository.SystemNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final OrderRepository orderRepository;
    private final LeadRepository leadRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final com.ghasl_service.demo.repository.SystemNotificationRepository systemNotificationRepository;

    public ReportService(OrderRepository orderRepository,
            LeadRepository leadRepository,
            ServiceCategoryRepository serviceCategoryRepository,
            com.ghasl_service.demo.repository.SystemNotificationRepository systemNotificationRepository) {
        this.orderRepository = orderRepository;
        this.leadRepository = leadRepository;
        this.serviceCategoryRepository = serviceCategoryRepository;
        this.systemNotificationRepository = systemNotificationRepository;
    }

    // ── Daily Report ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public String generateDailyReport() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);

        LocalDateTime prevStart = today.minusDays(1).atStartOfDay();
        LocalDateTime prevEnd = today.minusDays(1).atTime(LocalTime.MAX);

        return buildReport(
                "ڕاپۆرتی ڕۆژانە — " + today,
                start, end, prevStart, prevEnd);
    }

    // ── Weekly Report ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public String generateWeeklyReport() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = today.with(DayOfWeek.SUNDAY);

        LocalDateTime start = weekStart.atStartOfDay();
        LocalDateTime end = weekEnd.atTime(LocalTime.MAX);

        LocalDateTime prevStart = weekStart.minusWeeks(1).atStartOfDay();
        LocalDateTime prevEnd = weekEnd.minusWeeks(1).atTime(LocalTime.MAX);

        return buildReport(
                "ڕاپۆرتی هەفتانە — " + weekStart + " بۆ " + weekEnd,
                start, end, prevStart, prevEnd);
    }

    // ── Monthly Report ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public String generateMonthlyReport() {
        YearMonth currentMonth = YearMonth.now();
        LocalDateTime start = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime end = currentMonth.atEndOfMonth().atTime(LocalTime.MAX);

        YearMonth prevMonth = currentMonth.minusMonths(1);
        LocalDateTime prevStart = prevMonth.atDay(1).atStartOfDay();
        LocalDateTime prevEnd = prevMonth.atEndOfMonth().atTime(LocalTime.MAX);

        String monthName = currentMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase();
        return buildReport(
                "ڕاپۆرتی مانگانە — " + monthName + " " + currentMonth.getYear(),
                start, end, prevStart, prevEnd);
    }

    // ── Core aggregation (returns typed DTO) ───────────────────────────────────────

    /**
     * Core aggregation method that returns strictly typed metrics.
     * This is the Single Source of Truth for all periodic report data.
     * String formatting is a terminal operation performed by formatters.
     */
    public ReportMetricsDTO buildReportMetrics(
            LocalDateTime start, LocalDateTime end,
            LocalDateTime prevStart, LocalDateTime prevEnd) {

        // ── Current period ─────────────────────────────────────────
        long totalLeads = leadRepository.countLeadsBetweenDates(start, end);
        long totalOrders = orderRepository.countOrdersBetweenDates(start, end);
        long accepted = orderRepository.countAcceptedOrdersBetweenDates(start, end);
        long rejected = orderRepository.countRejectedOrdersBetweenDates(start, end);
        long pending = totalOrders - accepted - rejected; // still PENDING
        long unpricedPending = orderRepository.countUnpricedPendingOrdersBetweenDates(start, end);
        BigDecimal revenue = orderRepository.sumConfirmedRevenueBetweenDates(start, end);
        if (revenue == null)
            revenue = BigDecimal.ZERO;

        // ── Previous period ────────────────────────────────────────
        BigDecimal prevRevenue = orderRepository.sumConfirmedRevenueBetweenDates(prevStart, prevEnd);
        if (prevRevenue == null)
            prevRevenue = BigDecimal.ZERO;

        // ── Calculate growth percentage ────────────────────────────────
        BigDecimal revenueGrowthPercentage = calculateRevenueGrowthPercentage(revenue, prevRevenue);

        // ── Build and return DTO ────────────────────────────────────────
        return ReportMetricsDTO.builder()
                .periodStart(start)
                .periodEnd(end)
                .previousPeriodStart(prevStart)
                .previousPeriodEnd(prevEnd)
                .totalLeads(totalLeads)
                .totalOrders(totalOrders)
                .acceptedOrders(accepted)
                .rejectedOrders(rejected)
                .pendingOrders(pending)
                .unpricedPendingOrders(unpricedPending)
                .totalRevenue(revenue)
                .previousPeriodRevenue(prevRevenue)
                .revenueGrowthPercentage(revenueGrowthPercentage)
                .build();
    }

    // ── String formatter (terminal operation) ─────────────────────────────────────

    /**
     * Formats ReportMetricsDTO into Kurdish text for notifications.
     * This is a terminal operation - data flows as typed objects, formatting happens last.
     * 
     * Package-private for SSOT compliance with ReportGenerationService
     */
    String formatReportAsKurdishText(String title, ReportMetricsDTO metrics) {
        // Use OrderItem join to capture service breakdown across all order types (POS + Lead-based)
        List<Object[]> serviceBreakdown = orderRepository.countServicesByOrderItemBetweenDates(
                metrics.periodStart(), metrics.periodEnd());

        // ── Build string ──────────────────────────────────────────
        StringBuilder sb = new StringBuilder();

        // Header (bold, no Markdown asterisks inside Kurdish — use plain bold via
        // Telegram Markdown)
        sb.append("*").append(title).append("*\n\n");

        // Section 1: Total inquiries + comparison
        sb.append("📋 *کۆی داواکارییەکان:* ").append(metrics.totalLeads())
                .append(" ").append(compareCount(metrics.totalLeads(), 
                        leadRepository.countLeadsBetweenDates(metrics.previousPeriodStart(), metrics.previousPeriodEnd())))
                .append("\n");

        // Section 2–4: Work status breakdown (always shown)
        sb.append("✅ وەرگیراو: ").append(metrics.acceptedOrders()).append("\n");
        sb.append("❌ ڕەتکراوە: ").append(metrics.rejectedOrders()).append("\n");
        sb.append("⏳ چاوەڕوانی بڕیاری تۆیە: ").append(metrics.pendingOrders()).append("\n");

        // Section 5: Pending pricing
        if (metrics.unpricedPendingOrders() > 0) {
            sb.append("⚠️ چاوەڕوانی نرخ: ").append(metrics.unpricedPendingOrders())
                    .append(" ئۆردەر — تکایە نرخ دابنێ\n");
        } else {
            sb.append("⚠️ چاوەڕوانی نرخ: 0\n");
        }

        // Section 6: Revenue + comparison
        sb.append("\n💰 *کۆی داهات:* ").append(metrics.totalRevenue().longValue()).append(" دینار")
                .append(" ").append(compareRevenue(metrics.totalRevenue(), metrics.previousPeriodRevenue())).append("\n");

        // Section 7: Per-service breakdown (includes POS orders via OrderItem joins)
        sb.append("\n📊 *پۆلێنکردنی جۆرەکانی خزمەتگوزاری:*\n");
        if (serviceBreakdown.isEmpty()) {
            sb.append("   — هیچ داواکارییەک نییە\n");
        } else {
            for (Object[] row : serviceBreakdown) {
                String serviceEnglishName = row[0] != null ? String.valueOf(row[0]) : "UNKNOWN";
                long count = ((Number) row[1]).longValue();
                
                // Fetch Kurdish name dynamically from ServiceCategory
                String kurdishName = serviceCategoryRepository.findByEnglishName(serviceEnglishName)
                    .map(sc -> sc.getKurdishName())
                    .orElse(serviceEnglishName);
                
                sb.append("   • ").append(kurdishName)
                        .append(": ").append(count).append("\n");
            }
        }

        return sb.toString().trim();
    }

    /**
     * Legacy method for backward compatibility with scheduled reports.
     * Delegates to buildReportMetrics and formats the result.
     */
    String buildReport(String title,
            LocalDateTime start, LocalDateTime end,
            LocalDateTime prevStart, LocalDateTime prevEnd) {
        ReportMetricsDTO metrics = buildReportMetrics(start, end, prevStart, prevEnd);
        return formatReportAsKurdishText(title, metrics);
    }

    // ── Calculation helpers ────────────────────────────────────────────────────

    private BigDecimal calculateRevenueGrowthPercentage(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        BigDecimal diff = current.subtract(previous);
        return diff.divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ── Comparison helpers ────────────────────────────────────────────────────

    private String compareCount(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? "(نوێ)" : "";
        }
        long diff = current - previous;
        if (diff == 0)
            return "(= ماوەی پێشوو)";
        if (diff > 0)
            return "(▲" + diff + " زیاتر لە ماوەی پێشوو)";
        return "(▼" + Math.abs(diff) + " کەمتر لە ماوەی پێشوو)";
    }

    private String compareRevenue(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? "(نوێ)" : "";
        }
        BigDecimal diff = current.subtract(previous);
        if (diff.compareTo(BigDecimal.ZERO) == 0)
            return "(= ماوەی پێشوو)";
        BigDecimal pct = diff.divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP);
        if (diff.compareTo(BigDecimal.ZERO) > 0)
            return "(▲" + pct + "% زیاتر لە ماوەی پێشوو)";
        return "(▼" + pct.abs() + "% کەمتر لە ماوەی پێشوو)";
    }

    /** 
     * Returns the Kurdish unit label for quantity based on MeasurementUnit.
     * This replaces the legacy rugType-based method for dynamic service architecture.
     */
    public String quantityUnit(MeasurementUnit measurementUnit) {
        if (measurementUnit == null) {
            return "دانە";  // Default unit
        }
        switch (measurementUnit) {
            case PER_METER:
            case PER_SQUARE_METER:
                return "مەتر";
            case PER_PIECE:
            case COUNT:
                return "دانە";
            case PER_PERSON:
                return "نەفەر";
            case HOURLY:
                return "کاتژمێر";
            case PER_KILOGRAM:
                return "کیلۆگرام";
            case PER_LITER:
                return "لتر";
            case JOB:
                return "کار";
            default:
                return measurementUnit.name();
        }
    }

    // ── Scheduled senders ────────────────────────────────────────────────────

    @Scheduled(cron = "0 0 23 * * ?")
    public void sendScheduledDailyReport() {
        log.info("Saving scheduled daily report to database");
        String reportContent = generateDailyReport();
        com.ghasl_service.demo.model.SystemNotification notification = 
            new com.ghasl_service.demo.model.SystemNotification(
                com.ghasl_service.demo.model.SystemNotification.NotificationType.DAILY, 
                reportContent
            );
        systemNotificationRepository.save(notification);
        log.info("Daily report saved successfully");
    }

    @Scheduled(cron = "0 0 23 ? * THU")
    public void sendScheduledWeeklyReport() {
        log.info("Saving scheduled weekly report to database");
        String reportContent = generateWeeklyReport();
        com.ghasl_service.demo.model.SystemNotification notification = 
            new com.ghasl_service.demo.model.SystemNotification(
                com.ghasl_service.demo.model.SystemNotification.NotificationType.WEEKLY, 
                reportContent
            );
        systemNotificationRepository.save(notification);
        log.info("Weekly report saved successfully");
    }

    @Scheduled(cron = "0 0 23 L * ?")
    public void sendScheduledMonthlyReport() {
        log.info("Saving scheduled monthly report to database");
        String reportContent = generateMonthlyReport();
        com.ghasl_service.demo.model.SystemNotification notification = 
            new com.ghasl_service.demo.model.SystemNotification(
                com.ghasl_service.demo.model.SystemNotification.NotificationType.MONTHLY, 
                reportContent
            );
        systemNotificationRepository.save(notification);
        log.info("Monthly report saved successfully");
    }

    // ── Pareto Analysis (80/20 Rule) ───────────────────────────────────────

    /**
     * Returns Pareto analysis data for service profit distribution.
     * Uses PostgreSQL Window Functions for efficient single-pass calculation.
     * Only includes ACCEPTED orders (excludes REJECTED and PENDING).
     */
    @Transactional(readOnly = true)
    public ParetoAnalysisResponse getParetoAnalysis(String period) {
        LocalDateTime startDate = getStartDateForPeriod(period);
        LocalDateTime endDate = getEndDateForPeriod(period);

        List<Object[]> rawData = orderRepository.getParetoAnalysisData(startDate, endDate);

        List<ParetoAnalysisDTO> dtos = rawData.stream()
            .map(row -> {
                // Safely extract names
                String englishName = row[0] != null ? row[0].toString() : "Unknown";
                String kurdishName = row[1] != null ? row[1].toString() : "نەزانراو";
                
                // Log if englishName is "Unknown" (indicates JOIN failure)
                if ("Unknown".equals(englishName)) {
                    log.warn("Pareto analysis: Found service with unknown english_name. Row data: {}", java.util.Arrays.toString(row));
                }
                
                // Safely convert any numeric type (Double, Long, BigInteger, etc.) to BigDecimal
                BigDecimal absoluteProfit = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
                BigDecimal cumulativeProfit = row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
                BigDecimal totalProfit = row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;
                BigDecimal cumulativePercentage = row[5] != null ? new BigDecimal(row[5].toString()) : BigDecimal.ZERO;

                return new ParetoAnalysisDTO(
                    englishName,
                    kurdishName,
                    absoluteProfit,
                    cumulativeProfit,
                    totalProfit,
                    cumulativePercentage
                );
            })
            .collect(Collectors.toList());

        return new ParetoAnalysisResponse(dtos, "IQD", period);
    }

    private LocalDateTime getStartDateForPeriod(String period) {
        LocalDate today = LocalDate.now();
        switch (period.toLowerCase()) {
            case "today":
                return today.atStartOfDay();
            case "week":
                return today.with(DayOfWeek.MONDAY).atStartOfDay();
            case "month":
                return YearMonth.now().atDay(1).atStartOfDay();
            default:
                return today.atStartOfDay();
        }
    }

    /**
     * Helper method to get end date for a given period.
     */
    private LocalDateTime getEndDateForPeriod(String period) {
        LocalDate today = LocalDate.now();
        switch (period.toLowerCase()) {
            case "today":
                return today.atTime(LocalTime.MAX);
            case "week":
                return today.with(DayOfWeek.SUNDAY).atTime(LocalTime.MAX);
            case "month":
                return YearMonth.now().atEndOfMonth().atTime(LocalTime.MAX);
            default:
                return today.atTime(LocalTime.MAX);
        }
    }
}
