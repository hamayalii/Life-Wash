package com.ghasl_service.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ServicePricingUpdateRequest {
    private BigDecimal basePrice;
    private BigDecimal discountPrice;
    private BigDecimal discountPercentage;
    private LocalDateTime discountStartDate;
    private LocalDateTime discountEndDate;
    private Boolean isDiscountActive;
    private Integer sofaStandardSetSize;
    
    // Getters and Setters
    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
    
    public BigDecimal getDiscountPrice() { return discountPrice; }
    public void setDiscountPrice(BigDecimal discountPrice) { this.discountPrice = discountPrice; }
    
    public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public void setDiscountPercentage(BigDecimal discountPercentage) { this.discountPercentage = discountPercentage; }
    
    public LocalDateTime getDiscountStartDate() { return discountStartDate; }
    public void setDiscountStartDate(LocalDateTime discountStartDate) { this.discountStartDate = discountStartDate; }
    
    public LocalDateTime getDiscountEndDate() { return discountEndDate; }
    public void setDiscountEndDate(LocalDateTime discountEndDate) { this.discountEndDate = discountEndDate; }
    
    public Boolean getIsDiscountActive() { return isDiscountActive; }
    public void setIsDiscountActive(Boolean isDiscountActive) { this.isDiscountActive = isDiscountActive; }
    
    public Integer getSofaStandardSetSize() { return sofaStandardSetSize; }
    public void setSofaStandardSetSize(Integer sofaStandardSetSize) { this.sofaStandardSetSize = sofaStandardSetSize; }
}
