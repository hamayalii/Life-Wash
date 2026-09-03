package com.ghasl_service.demo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * DTO for updating marketing spend.
 * Used when admin manually updates ad spend via the report page.
 */
public class MarketingSpendUpdateRequest {
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0", message = "Amount must be non-negative")
    private BigDecimal amount;
    
    @NotNull(message = "Period is required")
    @Pattern(regexp = "today|week|month", message = "Period must be 'today', 'week', or 'month'")
    private String period; // "today", "week", "month"
    
    public MarketingSpendUpdateRequest() {
    }
    
    public MarketingSpendUpdateRequest(BigDecimal amount, String period) {
        this.amount = amount;
        this.period = period;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    
    public String getPeriod() {
        return period;
    }
    
    public void setPeriod(String period) {
        this.period = period;
    }
    
    @Override
    public String toString() {
        return "MarketingSpendUpdateRequest{amount=" + amount + ", period='" + period + "'}";
    }
}
