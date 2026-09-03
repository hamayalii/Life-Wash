package com.ghasl_service.demo.controller;

import com.ghasl_service.demo.dto.*;
import com.ghasl_service.demo.dto.ParetoAnalysisResponse;
import com.ghasl_service.demo.model.MeasurementUnit;
import com.ghasl_service.demo.model.Order;
import com.ghasl_service.demo.repository.ExpenseRepository;
import com.ghasl_service.demo.repository.LeadRepository;
import com.ghasl_service.demo.repository.MarketingSpendRepository;
import com.ghasl_service.demo.repository.OrderRepository;
import com.ghasl_service.demo.repository.ServiceCategoryRepository;
import com.ghasl_service.demo.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for the admin dashboard endpoints.
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardController.class);
    private static final Duration SESSION_GAP_THRESHOLD = Duration.ofMinutes(30);

    private final OrderRepository orderRepository;
    private final LeadRepository leadRepository;
    private final ReportService reportService;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final ExpenseRepository expenseRepository;
    private final MarketingSpendRepository marketingSpendRepository;

    public AdminDashboardController(OrderRepository orderRepository, LeadRepository leadRepository,
            ReportService reportService, ServiceCategoryRepository serviceCategoryRepository,
            ExpenseRepository expenseRepository, MarketingSpendRepository marketingSpendRepository) {
        this.orderRepository = orderRepository;
        this.leadRepository = leadRepository;
        this.reportService = reportService;
        this.serviceCategoryRepository = serviceCategoryRepository;
        this.expenseRepository = expenseRepository;
        this.marketingSpendRepository = marketingSpendRepository;
    }

    /**
     * GET /settings - Serve the settings page
     */
    @GetMapping("/settings")
    public String settingsPage() {
        return "settings.html";
    }

    /**
     * GET /api/v1/admin/dashboard/summary?period={today|week|month}
     */
    @GetMapping("/summary")
    public DashboardSummaryResponse getSummary(@RequestParam(defaultValue = "month") String period) {
        LocalDateTime[] currentPeriod = getDateRange(period);
        LocalDateTime[] previousPeriod = getPreviousDateRange(period);

        LocalDateTime currentStart = currentPeriod[0];
        LocalDateTime currentEnd = currentPeriod[1];
        LocalDateTime previousStart = previousPeriod[0];
        LocalDateTime previousEnd = previousPeriod[1];

        // Customers: session-based clustering (Q7)
        long customers = countCustomerSessions(currentStart, currentEnd);

        // Orders: total orders in period (ALL statuses including PENDING)
        // Business Rule: Order Volume Metrics must aggregate ALL orders including
        // PENDING
        // Financial Metrics (Revenue, Profit) are filtered to ACCEPTED only
        long orders = orderRepository.countOrdersBetweenDates(currentStart, currentEnd);

        // Orders trend calculation (month-over-month)
        long previousOrders = orderRepository.countOrdersBetweenDates(previousStart, previousEnd);
        BigDecimal currentOrdersBD = BigDecimal.valueOf(orders);
        BigDecimal previousOrdersBD = BigDecimal.valueOf(previousOrders);
        double ordersChangePercentage = calculateGrowthPercent(currentOrdersBD, previousOrdersBD);
        boolean isOrdersTrendPositive = orders >= previousOrders;

        // Growth: revenue comparison (weekly)
        BigDecimal currentRevenue = orderRepository.sumConfirmedRevenueBetweenDates(currentStart, currentEnd);
        if (currentRevenue == null)
            currentRevenue = BigDecimal.ZERO;

        BigDecimal previousRevenue = orderRepository.sumConfirmedRevenueBetweenDates(previousStart, previousEnd);
        if (previousRevenue == null)
            previousRevenue = BigDecimal.ZERO;

        double weeklyGrowthPercent = calculateGrowthPercent(currentRevenue, previousRevenue);

        // Monthly growth for Growth card
        YearMonth currentMonth = YearMonth.now();
        YearMonth previousMonth = YearMonth.now().minusMonths(1);
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = currentMonth.atEndOfMonth().atTime(LocalTime.MAX);
        LocalDateTime prevMonthStart = previousMonth.atDay(1).atStartOfDay();
        LocalDateTime prevMonthEnd = previousMonth.atEndOfMonth().atTime(LocalTime.MAX);

        BigDecimal currentMonthRevenue = orderRepository.sumConfirmedRevenueBetweenDates(monthStart, monthEnd);
        if (currentMonthRevenue == null)
            currentMonthRevenue = BigDecimal.ZERO;

        BigDecimal previousMonthRevenue = orderRepository.sumConfirmedRevenueBetweenDates(prevMonthStart, prevMonthEnd);
        if (previousMonthRevenue == null)
            previousMonthRevenue = BigDecimal.ZERO;

        double monthlyGrowthPercent = calculateGrowthPercent(currentMonthRevenue, previousMonthRevenue);

        // Profit: confirmedRevenue - monthlyExpenses (placeholder)
        BigDecimal profit = currentRevenue.subtract(monthlyExpenses());

        // Get monthly profits data
        List<DashboardSummaryResponse.MonthlyProfitData> monthlyProfits = getMonthlyProfits(12);

        DashboardSummaryResponse response = new DashboardSummaryResponse();
        response.setCustomers(customers);
        response.setOrders(orders);
        response.setWeeklyGrowthPercent(weeklyGrowthPercent);
        response.setMonthlyGrowthPercent(monthlyGrowthPercent);
        response.setOrdersChangePercentage(ordersChangePercentage);
        response.setIsOrdersTrendPositive(isOrdersTrendPositive);
        response.setProfit(profit);
        response.setCurrency("IQD");
        response.setMonthlyProfits(monthlyProfits);

        return response;
    }

    /**
     * GET /api/v1/admin/dashboard/monthly-profits?months=12
     * Returns monthly profit data for the specified number of months
     * Formula: Monthly Profit = Income - Expenses - Marketing Spend
     * Now uses ReportService.buildReportMetrics() for SSOT compliance
     */
    @GetMapping("/monthly-profits")
    public List<DashboardSummaryResponse.MonthlyProfitData> getMonthlyProfits(
            @RequestParam(defaultValue = "12") int months) {
        List<DashboardSummaryResponse.MonthlyProfitData> profits = new ArrayList<>();

        for (int i = months - 1; i >= 0; i--) {
            YearMonth targetMonth = YearMonth.now().minusMonths(i);
            String period = targetMonth.toString();
            String monthName = targetMonth.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);

            // Calculate income using ReportService.buildReportMetrics() for SSOT
            LocalDateTime monthStart = targetMonth.atDay(1).atStartOfDay();
            LocalDateTime monthEnd = targetMonth.plusMonths(1).atDay(1).atStartOfDay();
            YearMonth previousYearMonth = targetMonth.minusYears(1);
            LocalDateTime previousYearStart = previousYearMonth.atDay(1).atStartOfDay();
            LocalDateTime previousYearEnd = previousYearMonth.plusMonths(1).atDay(1).atStartOfDay();

            // Get typed metrics from ReportService (SSOT)
            com.ghasl_service.demo.dto.ReportMetricsDTO metrics = reportService.buildReportMetrics(monthStart, monthEnd,
                    previousYearStart, previousYearEnd);

            BigDecimal income = metrics.totalRevenue();

            // Calculate expenses (not included in ReportMetricsDTO)
            BigDecimal expenses = expenseRepository.sumAmountByPeriod(period);
            if (expenses == null)
                expenses = BigDecimal.ZERO;

            // Calculate marketing spend (not included in ReportMetricsDTO)
            BigDecimal marketingSpend = marketingSpendRepository.sumAmountByPeriod(targetMonth.toString());
            if (marketingSpend == null)
                marketingSpend = BigDecimal.ZERO;

            // Calculate profit: Income - Expenses - Marketing Spend
            BigDecimal profit = income.subtract(expenses).subtract(marketingSpend);

            // Calculate previous year profit
            BigDecimal previousYearIncome = metrics.previousPeriodRevenue();
            BigDecimal previousYearExpenses = expenseRepository.sumAmountByPeriod(previousYearMonth.toString());
            if (previousYearExpenses == null)
                previousYearExpenses = BigDecimal.ZERO;
            BigDecimal previousYearMarketingSpend = marketingSpendRepository
                    .sumAmountByPeriod(previousYearMonth.toString());
            if (previousYearMarketingSpend == null)
                previousYearMarketingSpend = BigDecimal.ZERO;
            BigDecimal previousYearProfit = previousYearIncome.subtract(previousYearExpenses)
                    .subtract(previousYearMarketingSpend);

            DashboardSummaryResponse.MonthlyProfitData data = new DashboardSummaryResponse.MonthlyProfitData();
            data.setMonth(monthName);
            data.setPeriod(period);
            data.setProfit(profit);
            data.setPreviousYearProfit(previousYearProfit);

            profits.add(data);
        }

        return profits;
    }

    /**
     * GET /api/v1/admin/dashboard/revenue-trend?period={today|week|month}
     */
    @GetMapping("/revenue-trend")
    public List<RevenueTrendResponse> getRevenueTrend(@RequestParam(defaultValue = "week") String period) {
        List<RevenueTrendResponse> trend = new ArrayList<>();

        if ("today".equals(period)) {
            // Hourly buckets for today
            LocalDate today = LocalDate.now();
            for (int hour = 0; hour < 24; hour++) {
                LocalDateTime hourStart = today.atTime(LocalTime.of(hour, 0));
                LocalDateTime hourEnd = today.atTime(LocalTime.of(hour, 59, 59, 999999999));
                BigDecimal amount = orderRepository.sumConfirmedRevenueBetweenDates(hourStart, hourEnd);
                if (amount == null)
                    amount = BigDecimal.ZERO;
                trend.add(new RevenueTrendResponse(String.format("%02d:00", hour), amount));
            }
        } else if ("week".equals(period)) {
            // Daily buckets for current week
            LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
            for (int i = 0; i < 7; i++) {
                LocalDate day = weekStart.plusDays(i);
                LocalDateTime dayStart = day.atStartOfDay();
                LocalDateTime dayEnd = day.atTime(LocalTime.MAX);
                BigDecimal amount = orderRepository.sumConfirmedRevenueBetweenDates(dayStart, dayEnd);
                if (amount == null)
                    amount = BigDecimal.ZERO;
                trend.add(new RevenueTrendResponse(day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                        amount));
            }
        } else if ("month".equals(period)) {
            // Daily buckets for current month
            YearMonth currentMonth = YearMonth.now();
            for (int day = 1; day <= currentMonth.lengthOfMonth(); day++) {
                LocalDate date = currentMonth.atDay(day);
                LocalDateTime dayStart = date.atStartOfDay();
                LocalDateTime dayEnd = date.atTime(LocalTime.MAX);
                BigDecimal amount = orderRepository.sumConfirmedRevenueBetweenDates(dayStart, dayEnd);
                if (amount == null)
                    amount = BigDecimal.ZERO;
                trend.add(new RevenueTrendResponse(String.valueOf(day), amount));
            }
        }

        return trend;
    }

    /**
     * GET /api/v1/admin/dashboard/top-services?period={today|week|month}
     * Updated to use dynamic service aggregation via ServiceCategory
     */
    @GetMapping("/top-services")
    public List<TopServiceResponse> getTopServices(@RequestParam(defaultValue = "month") String period) {
        LocalDateTime[] dateRange = getDateRange(period);
        LocalDateTime start = dateRange[0];
        LocalDateTime end = dateRange[1];

        // Use new dynamic service aggregation query (groups by
        // ServiceCategory.englishName)
        List<Object[]> breakdown = orderRepository.countOrdersByServiceBetweenDates(start, end,
                Order.WorkStatus.PENDING);

        return breakdown.stream()
                .map(row -> {
                    String serviceEnglishName = row[0] != null ? String.valueOf(row[0]) : "UNKNOWN";
                    long count = ((Number) row[1]).longValue();

                    // Fetch Kurdish name dynamically from ServiceCategory
                    String kurdishName = serviceCategoryRepository.findByEnglishName(serviceEnglishName)
                            .map(sc -> sc.getKurdishName())
                            .orElse(serviceEnglishName);

                    return new TopServiceResponse(serviceEnglishName, kurdishName, count);
                })
                .collect(Collectors.toList());
    }

    /**
     * GET /api/v1/admin/dashboard/requests?page=0&size=10
     * Returns paginated orders with server-side pagination
     */
    @GetMapping("/requests")
    public org.springframework.data.domain.Page<RequestResponse> getRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,
                        "createdAt"));

        org.springframework.data.domain.Page<Order> ordersPage = orderRepository.findAll(pageable);

        return ordersPage.map(order -> {
            // Handle POS orders (with OrderItems) vs legacy Lead orders (flat structure)
            String serviceNamesFormatted;
            String quantityLabel;
            BigDecimal quantity;
            BigDecimal price;

            if (order.getItems() != null && !order.getItems().isEmpty()) {
                // POS order: aggregate from OrderItems
                serviceNamesFormatted = order.getItems().stream()
                        .map(item -> item.getServiceCategory() != null ? item.getServiceCategory().getKurdishName()
                                : "")
                        .filter(name -> !name.isEmpty())
                        .collect(Collectors.joining(", "));

                quantityLabel = order.getItems().stream()
                        .map(item -> {
                            String unit = item.getUnitName() != null ? translateUnit(item.getUnitName()) : "دانە";
                            BigDecimal qty = item.getQuantity();
                            return qty != null ? String.format("%.1f %s", qty, unit) : "-";
                        })
                        .collect(Collectors.joining(", "));

                quantity = order.getItems().stream()
                        .map(item -> item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                price = order.getGrandTotal(); // POS orders use grandTotal
            } else {
                // Legacy Lead order: use flat fields
                String rugType = order.getRugType();
                serviceNamesFormatted = translateLegacyRugTypeToKurdish(rugType);
                String unit = translateUnitFromLegacyRugType(rugType);
                quantityLabel = order.getQuantity() != null && !unit.isEmpty()
                        ? order.getQuantity() + " " + unit
                        : order.getQuantity() != null ? String.valueOf(order.getQuantity()) : "-";
                quantity = order.getQuantity();
                // Single Source of Truth: Use grandTotal for all orders (POS and legacy)
                price = order.getGrandTotal() != null ? order.getGrandTotal() : order.getPrice();
            }

            return new RequestResponse(
                    order.getId() != null ? order.getId() : 0L,
                    order.getCustomerName() != null ? order.getCustomerName() : "",
                    order.getCreatedAt() != null ? order.getCreatedAt().toLocalDate().toString() : "",
                    serviceNamesFormatted != null ? serviceNamesFormatted : "",
                    quantity != null ? quantity : BigDecimal.ZERO,
                    quantityLabel != null ? quantityLabel : "",
                    price != null ? price : BigDecimal.ZERO,
                    order.getWorkStatus() != null ? order.getWorkStatus().name() : "PENDING",
                    order.getAddress() != null ? order.getAddress() : "",
                    order.getMessage() != null ? order.getMessage() : "");
        });
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    /**
     * Translates unit name string to Kurdish representations.
     * Handles both enum names and display names for backward compatibility.
     */
    private String translateUnitFromUnitName(String unitName) {
        if (unitName == null || unitName.trim().isEmpty()) {
            return "دانە"; // Default fallback
        }

        String normalized = unitName.trim().toLowerCase();
        return switch (normalized) {
            case "per_meter", "per_square_meter", "meter", "مەتر" -> "مەتر";
            case "per_piece", "count", "piece", "دانە" -> "دانە";
            case "per_person", "person", "نەفەر" -> "نەفەر";
            case "hourly", "hour", "کاتژمێر" -> "کاتژمێر";
            case "per_kilogram", "kilogram", "کیلۆگرام" -> "کیلۆگرام";
            case "per_liter", "liter", "لتر" -> "لتر";
            case "job", "کار" -> "کار";
            default -> unitName; // Return original if not in dictionary
        };
    }

    /**
     * Translates MeasurementUnit enum to Kurdish representations.
     * Ensures UI localization for measurement units.
     * This replaces the legacy string-based translation for dynamic service
     * architecture.
     */
    private String translateUnit(MeasurementUnit measurementUnit) {
        if (measurementUnit == null) {
            return "دانە"; // Default fallback
        }

        return switch (measurementUnit) {
            case PER_METER, PER_SQUARE_METER -> "مەتر";
            case PER_PIECE, COUNT -> "دانە";
            case PER_PERSON -> "نەفەر";
            case HOURLY -> "کاتژمێر";
            case PER_KILOGRAM -> "کیلۆگرام";
            case PER_LITER -> "لتر";
            case JOB -> "کار";
        };
    }

    /**
     * Legacy helper for translating old rugType strings to Kurdish service names.
     * Used only for historical Lead orders that don't have OrderItems.
     * This ensures backward compatibility with existing data.
     */
    private String translateLegacyRugTypeToKurdish(String rugType) {
        if (rugType == null || rugType.trim().isEmpty()) {
            return ""; // Default fallback - empty string
        }

        String normalized = rugType.trim().toLowerCase();
        return switch (normalized) {
            case "rug", "carpet", "shag", "persian" -> "فەرش";
            case "silk", "synthetic" -> "بەتانی";
            case "wool" -> "قەنەفە";
            case "antique" -> "پاککردنەوەی ماڵ/شوقە/باخ";
            default -> rugType; // Return original if not in dictionary
        };
    }

    /**
     * Legacy helper for translating old rugType strings to Kurdish units.
     * Used only for historical Lead orders that don't have OrderItems.
     * This ensures backward compatibility with existing data.
     */
    private String translateUnitFromLegacyRugType(String rugType) {
        if (rugType == null || rugType.trim().isEmpty()) {
            return "دانە"; // Default fallback
        }

        String normalized = rugType.trim().toLowerCase();
        return switch (normalized) {
            case "rug", "carpet", "shag", "persian" -> "مەتر";
            case "silk", "synthetic" -> "دانە";
            case "wool" -> "نەفەر";
            case "antique" -> ""; // antique has no meaningful unit
            default -> rugType; // Return original if not in dictionary
        };
    }

    /**
     * Q7: Session-based customer counting.
     * Groups ACCEPTED orders by phoneNumber, then clusters them into sessions.
     * Orders within 30 minutes of each other (same phone) = same session.
     */
    private long countCustomerSessions(LocalDateTime start, LocalDateTime end) {
        List<Order> acceptedOrders = orderRepository.findAcceptedOrdersForSessionClustering(start, end);

        if (acceptedOrders.isEmpty()) {
            return 0;
        }

        long sessionCount = 0;
        String currentPhone = null;
        LocalDateTime lastOrderTime = null;

        for (Order order : acceptedOrders) {
            String phone = order.getPhoneNumber();
            LocalDateTime orderTime = order.getCreatedAt();

            if (currentPhone == null || !currentPhone.equals(phone)) {
                // New phone number = new session
                sessionCount++;
                currentPhone = phone;
                lastOrderTime = orderTime;
            } else {
                // Same phone: check time gap
                Duration gap = Duration.between(lastOrderTime, orderTime);
                if (gap.compareTo(SESSION_GAP_THRESHOLD) > 0) {
                    // Gap > 30 minutes = new session
                    sessionCount++;
                }
                // Gap <= 30 minutes = same session (no increment)
                lastOrderTime = orderTime;
            }
        }

        return sessionCount;
    }

    /**
     * Placeholder for monthly expenses tracking.
     * TODO: Implement actual expense tracking when feature is added.
     */
    private BigDecimal monthlyExpenses() {
        return BigDecimal.ZERO; // Placeholder
    }

    private double calculateGrowthPercent(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;
        }
        BigDecimal diff = current.subtract(previous);
        BigDecimal pct = diff.divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        return pct.doubleValue();
    }

    private LocalDateTime[] getDateRange(String period) {
        LocalDate today = LocalDate.now();
        LocalDateTime start, end;

        switch (period) {
            case "today":
                start = today.atStartOfDay();
                end = today.atTime(LocalTime.MAX);
                break;
            case "week":
                LocalDate weekStart = today.with(DayOfWeek.MONDAY);
                LocalDate weekEnd = today.with(DayOfWeek.SUNDAY);
                start = weekStart.atStartOfDay();
                end = weekEnd.atTime(LocalTime.MAX);
                break;
            case "month":
                YearMonth currentMonth = YearMonth.now();
                start = currentMonth.atDay(1).atStartOfDay();
                end = currentMonth.atEndOfMonth().atTime(LocalTime.MAX);
                break;
            default:
                start = today.with(DayOfWeek.MONDAY).atStartOfDay();
                end = today.with(DayOfWeek.SUNDAY).atTime(LocalTime.MAX);
        }

        return new LocalDateTime[] { start, end };
    }

    private LocalDateTime[] getPreviousDateRange(String period) {
        LocalDate today = LocalDate.now();
        LocalDateTime start, end;

        switch (period) {
            case "today":
                LocalDate yesterday = today.minusDays(1);
                start = yesterday.atStartOfDay();
                end = yesterday.atTime(LocalTime.MAX);
                break;
            case "week":
                LocalDate weekStart = today.with(DayOfWeek.MONDAY);
                LocalDate weekEnd = today.with(DayOfWeek.SUNDAY);
                start = weekStart.minusWeeks(1).atStartOfDay();
                end = weekEnd.minusWeeks(1).atTime(LocalTime.MAX);
                break;
            case "month":
                YearMonth prevMonth = YearMonth.now().minusMonths(1);
                start = prevMonth.atDay(1).atStartOfDay();
                end = prevMonth.atEndOfMonth().atTime(LocalTime.MAX);
                break;
            default:
                LocalDate weekStartDefault = today.with(DayOfWeek.MONDAY);
                LocalDate weekEndDefault = today.with(DayOfWeek.SUNDAY);
                start = weekStartDefault.minusWeeks(1).atStartOfDay();
                end = weekEndDefault.minusWeeks(1).atTime(LocalTime.MAX);
        }

        return new LocalDateTime[] { start, end };
    }

}
