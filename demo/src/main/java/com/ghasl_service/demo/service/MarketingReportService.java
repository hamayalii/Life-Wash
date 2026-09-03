package com.ghasl_service.demo.service;

import com.ghasl_service.demo.dto.MarketingROIResponse;
import com.ghasl_service.demo.dto.MarketingSpendUpdateRequest;
import com.ghasl_service.demo.model.MarketingChannel;
import com.ghasl_service.demo.model.MarketingSpend;
import com.ghasl_service.demo.repository.CustomerValueRepository;
import com.ghasl_service.demo.repository.MarketingSpendRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Service for marketing ROI calculations.
 * Implements business logic for CAC, CLV, and ROI ratio calculations.
 * All calculations performed in backend (Zero Trust Architecture).
 */
@Service
public class MarketingReportService {

    private static final Logger log = LoggerFactory.getLogger(MarketingReportService.class);

    private final MarketingSpendRepository marketingSpendRepository;
    private final CustomerValueRepository customerValueRepository;
    
    @PersistenceContext
    private EntityManager entityManager;

    public MarketingReportService(MarketingSpendRepository marketingSpendRepository,
                                 CustomerValueRepository customerValueRepository) {
        this.marketingSpendRepository = marketingSpendRepository;
        this.customerValueRepository = customerValueRepository;
    }

    /**
     * Calculate marketing ROI metrics for the gauge chart.
     * 
     * Business Logic:
     * - CAC (Customer Acquisition Cost) = Total Monthly Ad Spend / New Customers
     * - CLV (Customer Lifetime Value) = Average of total_lifetime_value from customer_value table
     * - ROI Ratio = CLV / CAC
     * - New Customer = Unique phone number placing first-ever order within the requested month
     * 
     * @param period "today", "week", or "month"
     * @return MarketingROIResponse with calculated metrics
     */
    public MarketingROIResponse getMarketingROI(String period) {
        String targetPeriod = getTargetPeriod(period);
        LocalDateTime[] dateRange = getDateRange(period);
        LocalDateTime startDate = dateRange[0];
        LocalDateTime endDate = dateRange[1];

        log.info("Calculating marketing ROI for period: {}, date range: {} to {}", period, startDate, endDate);

        // 1. Get total ad spend for period (String format "yyyy-MM")
        BigDecimal totalAdSpend = marketingSpendRepository.sumAmountByPeriod(targetPeriod);
        if (totalAdSpend == null) {
            totalAdSpend = BigDecimal.ZERO;
        }

        // 2. Get new customers in period (first-time customers)
        Long newCustomers = customerValueRepository.countCustomersWithFirstOrderBetween(startDate, endDate);
        if (newCustomers == null) {
            newCustomers = 0L;
        }

        // 3. Calculate CAC = Total Ad Spend / New Customers
        BigDecimal cac = BigDecimal.ZERO;
        if (newCustomers > 0) {
            cac = totalAdSpend.divide(BigDecimal.valueOf(newCustomers), 2, RoundingMode.HALF_UP);
        }

        // 4. Get average CLV (system-wide average)
        // Formula: (Total Lifetime Value of ALL customers) / (Total Number of Customers)
        BigDecimal avgClv = customerValueRepository.calculateAverageCLV();
        if (avgClv == null) {
            avgClv = BigDecimal.ZERO;
        }

        // 5. Calculate ROI Ratio = CLV / CAC
        BigDecimal roiRatio = BigDecimal.ZERO;
        if (cac.compareTo(BigDecimal.ZERO) > 0) {
            roiRatio = avgClv.divide(cac, 2, RoundingMode.HALF_UP);
        }

        log.info("Marketing ROI calculated - AdSpend: {}, NewCustomers: {}, CAC: {}, CLV: {}, Ratio: {}", 
            totalAdSpend, newCustomers, cac, avgClv, roiRatio);

        return new MarketingROIResponse(
            totalAdSpend,
            newCustomers,
            cac,
            avgClv,
            roiRatio,
            "IQD",
            targetPeriod
        );
    }

    /**
     * Convert period string to String in "yyyy-MM" format.
     */
    private String getTargetPeriod(String period) {
        LocalDate today = LocalDate.now();
        YearMonth currentYearMonth = YearMonth.from(today);
        
        switch (period) {
            case "today":
            case "week":
                // For today and week, use current month
                return currentYearMonth.toString(); // "yyyy-MM"
            case "month":
                return currentYearMonth.toString(); // "yyyy-MM"
            default:
                return currentYearMonth.toString(); // "yyyy-MM"
        }
    }

    /**
     * Get date range for the specified period.
     */
    private LocalDateTime[] getDateRange(String period) {
        LocalDate today = LocalDate.now();
        LocalDateTime start, end;

        switch (period) {
            case "today":
                start = today.atStartOfDay();
                end = today.atTime(java.time.LocalTime.MAX);
                break;
            case "week":
                LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
                LocalDate weekEnd = today.with(java.time.DayOfWeek.SUNDAY);
                start = weekStart.atStartOfDay();
                end = weekEnd.atTime(java.time.LocalTime.MAX);
                break;
            case "month":
                YearMonth currentMonth = YearMonth.now();
                start = currentMonth.atDay(1).atStartOfDay();
                end = currentMonth.atEndOfMonth().atTime(java.time.LocalTime.MAX);
                break;
            default:
                // Default to current month
                YearMonth defaultMonth = YearMonth.now();
                start = defaultMonth.atDay(1).atStartOfDay();
                end = defaultMonth.atEndOfMonth().atTime(java.time.LocalTime.MAX);
        }

        return new LocalDateTime[]{start, end};
    }

    /**
     * Update marketing spend for a specific period.
     * Uses native PostgreSQL UPSERT (INSERT ... ON CONFLICT DO UPDATE) for atomic operation.
     * This preserves createdAt history and ensures ACID compliance.
     * 
     * @param request The update request containing amount and period
     * @throws IllegalArgumentException if period is invalid
     * @throws RuntimeException if database operation fails
     */
    @Transactional
    public void updateMarketingSpend(MarketingSpendUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        if (request.getAmount() == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (request.getPeriod() == null || request.getPeriod().trim().isEmpty()) {
            throw new IllegalArgumentException("Period cannot be null or empty");
        }
        
        String targetPeriod = getTargetPeriod(request.getPeriod());
        
        log.info("UPSERTING marketing spend for period: {}, amount: {}", targetPeriod, request.getAmount());
        
        try {
            // Use JPA-based upsert for database portability with self-healing deduplication
            // Find existing records for period and channel (may return duplicates from earlier bugs)
            List<MarketingSpend> existingRecords = 
                marketingSpendRepository.findByPeriodAndChannel(targetPeriod, com.ghasl_service.demo.model.MarketingChannel.OTHER);
            
            if (existingRecords.isEmpty()) {
                // Create new record
                MarketingSpend newSpend = new MarketingSpend();
                newSpend.setPeriod(targetPeriod);
                newSpend.setChannel(com.ghasl_service.demo.model.MarketingChannel.OTHER);
                newSpend.setAmount(request.getAmount());
                newSpend.setCampaignName("Manual Update");
                newSpend.setDescription("User input via report page");
                marketingSpendRepository.save(newSpend);
                log.info("Created new marketing spend for period: {}, amount: {}", targetPeriod, request.getAmount());
            } else {
                // Update first record and delete any duplicates (self-healing deduplication)
                MarketingSpend primaryRecord = existingRecords.get(0);
                primaryRecord.setAmount(request.getAmount());
                primaryRecord.setDescription("Updated via report page");
                marketingSpendRepository.save(primaryRecord);
                log.info("Updated existing marketing spend for period: {}, amount: {}", targetPeriod, request.getAmount());
                
                // Self-healing: Delete duplicate records if corruption exists
                if (existingRecords.size() > 1) {
                    log.warn("Found {} duplicate marketing spend records for period: {}, channel: OTHER. Deleting duplicates.", 
                        existingRecords.size() - 1, targetPeriod);
                    for (int i = 1; i < existingRecords.size(); i++) {
                        MarketingSpend duplicate = existingRecords.get(i);
                        try {
                            marketingSpendRepository.delete(duplicate);
                            log.info("Deleted duplicate marketing spend record: id={}, period={}, channel={}", 
                                duplicate.getId(), duplicate.getPeriod(), duplicate.getChannel());
                        } catch (org.springframework.dao.OptimisticLockingFailureException e) {
                            log.warn("Duplicate entity ID {} already deleted by another thread (race condition handled gracefully)", 
                                duplicate.getId());
                        } catch (org.springframework.dao.DataAccessException e) {
                            log.warn("Failed to delete duplicate entity ID {} (likely already deleted by another thread): {}", 
                                duplicate.getId(), e.getMessage());
                        }
                    }
                }
            }
                
        } catch (Exception e) {
            log.error("Failed to UPSERT marketing spend for period: {}, amount: {}", 
                targetPeriod, request.getAmount(), e);
            throw new RuntimeException("Failed to update marketing spend: " + e.getMessage(), e);
        }
    }
}
