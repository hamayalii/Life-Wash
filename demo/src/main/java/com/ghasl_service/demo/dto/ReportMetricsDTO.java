package com.ghasl_service.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Single Source of Truth (SSOT) for aggregated periodic report metrics.
 * Contains strictly typed fields for all business metrics.
 * String formatting is a terminal operation performed by formatters, not by
 * this DTO.
 */
public record ReportMetricsDTO(
        // Period information
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        LocalDateTime previousPeriodStart,
        LocalDateTime previousPeriodEnd,

        // Lead/Order counts
        Long totalLeads,
        Long totalOrders,
        Long acceptedOrders,
        Long rejectedOrders,
        Long pendingOrders,
        Long unpricedPendingOrders,

        // Financial metrics
        BigDecimal totalRevenue,
        BigDecimal previousPeriodRevenue,
        BigDecimal revenueGrowthPercentage

// Service breakdown (optional - can be added later)
// Map<String, Long> serviceBreakdown
) {
    /**
     * Builder pattern for easier construction
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;
        private LocalDateTime previousPeriodStart;
        private LocalDateTime previousPeriodEnd;
        private Long totalLeads = 0L;
        private Long totalOrders = 0L;
        private Long acceptedOrders = 0L;
        private Long rejectedOrders = 0L;
        private Long pendingOrders = 0L;
        private Long unpricedPendingOrders = 0L;
        private BigDecimal totalRevenue = BigDecimal.ZERO;
        private BigDecimal previousPeriodRevenue = BigDecimal.ZERO;
        private BigDecimal revenueGrowthPercentage = BigDecimal.ZERO;

        public Builder periodStart(LocalDateTime periodStart) {
            this.periodStart = periodStart;
            return this;
        }

        public Builder periodEnd(LocalDateTime periodEnd) {
            this.periodEnd = periodEnd;
            return this;
        }

        public Builder previousPeriodStart(LocalDateTime previousPeriodStart) {
            this.previousPeriodStart = previousPeriodStart;
            return this;
        }

        public Builder previousPeriodEnd(LocalDateTime previousPeriodEnd) {
            this.previousPeriodEnd = previousPeriodEnd;
            return this;
        }

        public Builder totalLeads(Long totalLeads) {
            this.totalLeads = totalLeads;
            return this;
        }

        public Builder totalOrders(Long totalOrders) {
            this.totalOrders = totalOrders;
            return this;
        }

        public Builder acceptedOrders(Long acceptedOrders) {
            this.acceptedOrders = acceptedOrders;
            return this;
        }

        public Builder rejectedOrders(Long rejectedOrders) {
            this.rejectedOrders = rejectedOrders;
            return this;
        }

        public Builder pendingOrders(Long pendingOrders) {
            this.pendingOrders = pendingOrders;
            return this;
        }

        public Builder unpricedPendingOrders(Long unpricedPendingOrders) {
            this.unpricedPendingOrders = unpricedPendingOrders;
            return this;
        }

        public Builder totalRevenue(BigDecimal totalRevenue) {
            this.totalRevenue = totalRevenue;
            return this;
        }

        public Builder previousPeriodRevenue(BigDecimal previousPeriodRevenue) {
            this.previousPeriodRevenue = previousPeriodRevenue;
            return this;
        }

        public Builder revenueGrowthPercentage(BigDecimal revenueGrowthPercentage) {
            this.revenueGrowthPercentage = revenueGrowthPercentage;
            return this;
        }

        public ReportMetricsDTO build() {
            return new ReportMetricsDTO(
                    periodStart,
                    periodEnd,
                    previousPeriodStart,
                    previousPeriodEnd,
                    totalLeads,
                    totalOrders,
                    acceptedOrders,
                    rejectedOrders,
                    pendingOrders,
                    unpricedPendingOrders,
                    totalRevenue,
                    previousPeriodRevenue,
                    revenueGrowthPercentage);
        }
    }
}
