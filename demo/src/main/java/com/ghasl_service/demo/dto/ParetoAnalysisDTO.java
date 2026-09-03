package com.ghasl_service.demo.dto;

import java.math.BigDecimal;

public class ParetoAnalysisDTO {
    private String serviceEnglishName;
    private String serviceKurdishName;
    private BigDecimal absoluteProfit;
    private BigDecimal cumulativeProfit;
    private BigDecimal totalProfit;
    private BigDecimal cumulativePercentage;

    public ParetoAnalysisDTO() {
    }

    public ParetoAnalysisDTO(String serviceEnglishName, String serviceKurdishName, 
                            BigDecimal absoluteProfit, BigDecimal cumulativeProfit,
                            BigDecimal totalProfit, BigDecimal cumulativePercentage) {
        this.serviceEnglishName = serviceEnglishName;
        this.serviceKurdishName = serviceKurdishName;
        this.absoluteProfit = absoluteProfit;
        this.cumulativeProfit = cumulativeProfit;
        this.totalProfit = totalProfit;
        this.cumulativePercentage = cumulativePercentage;
    }

    // Getters and Setters

    public String getServiceEnglishName() {
        return serviceEnglishName;
    }

    public void setServiceEnglishName(String serviceEnglishName) {
        this.serviceEnglishName = serviceEnglishName;
    }

    public String getServiceKurdishName() {
        return serviceKurdishName;
    }

    public void setServiceKurdishName(String serviceKurdishName) {
        this.serviceKurdishName = serviceKurdishName;
    }

    public BigDecimal getAbsoluteProfit() {
        return absoluteProfit;
    }

    public void setAbsoluteProfit(BigDecimal absoluteProfit) {
        this.absoluteProfit = absoluteProfit;
    }

    public BigDecimal getCumulativeProfit() {
        return cumulativeProfit;
    }

    public void setCumulativeProfit(BigDecimal cumulativeProfit) {
        this.cumulativeProfit = cumulativeProfit;
    }

    public BigDecimal getTotalProfit() {
        return totalProfit;
    }

    public void setTotalProfit(BigDecimal totalProfit) {
        this.totalProfit = totalProfit;
    }

    public BigDecimal getCumulativePercentage() {
        return cumulativePercentage;
    }

    public void setCumulativePercentage(BigDecimal cumulativePercentage) {
        this.cumulativePercentage = cumulativePercentage;
    }
}
