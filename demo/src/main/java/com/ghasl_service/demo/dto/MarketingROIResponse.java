package com.ghasl_service.demo.dto;

import java.math.BigDecimal;

/**
 * DTO for Marketing ROI response.
 * Contains metrics for the gauge chart: Ad Spend, New Customers, CAC, CLV, and ROI Ratio.
 * Period is stored as String in "yyyy-MM" format to avoid JPA YearMonth serialization issues.
 */
public class MarketingROIResponse {
    
    private BigDecimal monthlyAdSpend;
    private Long newCustomers;
    private BigDecimal customerAcquisitionCost;  // CAC
    private BigDecimal averageCustomerLifetimeValue;  // CLV
    private BigDecimal roiRatio;  // CLV / CAC
    private String currency;
    private String period; // Format: "yyyy-MM"
    
    public MarketingROIResponse() {
    }
    
    public MarketingROIResponse(BigDecimal monthlyAdSpend, Long newCustomers, 
                               BigDecimal customerAcquisitionCost, 
                               BigDecimal averageCustomerLifetimeValue,
                               BigDecimal roiRatio, String currency, String period) {
        this.monthlyAdSpend = monthlyAdSpend;
        this.newCustomers = newCustomers;
        this.customerAcquisitionCost = customerAcquisitionCost;
        this.averageCustomerLifetimeValue = averageCustomerLifetimeValue;
        this.roiRatio = roiRatio;
        this.currency = currency;
        this.period = period;
    }
    
    // Getters and Setters
    
    public BigDecimal getMonthlyAdSpend() {
        return monthlyAdSpend;
    }
    
    public void setMonthlyAdSpend(BigDecimal monthlyAdSpend) {
        this.monthlyAdSpend = monthlyAdSpend;
    }
    
    public Long getNewCustomers() {
        return newCustomers;
    }
    
    public void setNewCustomers(Long newCustomers) {
        this.newCustomers = newCustomers;
    }
    
    public BigDecimal getCustomerAcquisitionCost() {
        return customerAcquisitionCost;
    }
    
    public void setCustomerAcquisitionCost(BigDecimal customerAcquisitionCost) {
        this.customerAcquisitionCost = customerAcquisitionCost;
    }
    
    public BigDecimal getAverageCustomerLifetimeValue() {
        return averageCustomerLifetimeValue;
    }
    
    public void setAverageCustomerLifetimeValue(BigDecimal averageCustomerLifetimeValue) {
        this.averageCustomerLifetimeValue = averageCustomerLifetimeValue;
    }
    
    public BigDecimal getRoiRatio() {
        return roiRatio;
    }
    
    public void setRoiRatio(BigDecimal roiRatio) {
        this.roiRatio = roiRatio;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public String getPeriod() {
        return period;
    }
    
    public void setPeriod(String period) {
        this.period = period;
    }
}
