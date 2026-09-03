package com.ghasl_service.demo.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO for GET /api/v1/admin/dashboard/summary
 */
public class DashboardSummaryResponse {
    private long customers;
    private long orders;
    private double weeklyGrowthPercent;
    private double monthlyGrowthPercent;
    private double ordersChangePercentage;
    private boolean isOrdersTrendPositive;
    private BigDecimal profit;
    private String currency;
    private List<MonthlyProfitData> monthlyProfits;

    // Getters and Setters
    public long getCustomers() { return customers; }
    public void setCustomers(long customers) { this.customers = customers; }

    public long getOrders() { return orders; }
    public void setOrders(long orders) { this.orders = orders; }

    public double getWeeklyGrowthPercent() { return weeklyGrowthPercent; }
    public void setWeeklyGrowthPercent(double weeklyGrowthPercent) { this.weeklyGrowthPercent = weeklyGrowthPercent; }

    public double getMonthlyGrowthPercent() { return monthlyGrowthPercent; }
    public void setMonthlyGrowthPercent(double monthlyGrowthPercent) { this.monthlyGrowthPercent = monthlyGrowthPercent; }

    public double getOrdersChangePercentage() { return ordersChangePercentage; }
    public void setOrdersChangePercentage(double ordersChangePercentage) { this.ordersChangePercentage = ordersChangePercentage; }

    public boolean isOrdersTrendPositive() { return isOrdersTrendPositive; }
    public void setIsOrdersTrendPositive(boolean isOrdersTrendPositive) { this.isOrdersTrendPositive = isOrdersTrendPositive; }

    public BigDecimal getProfit() { return profit; }
    public void setProfit(BigDecimal profit) { this.profit = profit; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public List<MonthlyProfitData> getMonthlyProfits() { return monthlyProfits; }
    public void setMonthlyProfits(List<MonthlyProfitData> monthlyProfits) { this.monthlyProfits = monthlyProfits; }

    // Inner class for monthly profit data
    public static class MonthlyProfitData {
        private String month; // "Jan", "Feb", "Mar", etc.
        private String period; // "2026-01", "2026-02", etc.
        private BigDecimal profit;
        private BigDecimal previousYearProfit;

        // Getters and Setters
        public String getMonth() { return month; }
        public void setMonth(String month) { this.month = month; }

        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }

        public BigDecimal getProfit() { return profit; }
        public void setProfit(BigDecimal profit) { this.profit = profit; }

        public BigDecimal getPreviousYearProfit() { return previousYearProfit; }
        public void setPreviousYearProfit(BigDecimal previousYearProfit) { this.previousYearProfit = previousYearProfit; }
    }
}

