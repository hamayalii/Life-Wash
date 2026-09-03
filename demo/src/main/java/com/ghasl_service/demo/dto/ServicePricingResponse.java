package com.ghasl_service.demo.dto;

import com.ghasl_service.demo.model.ServicePricing;
import java.math.BigDecimal;

public class ServicePricingResponse {
    private Long id;
    private String serviceType;
    private String serviceTypeKurdish;
    private String pricingUnit;
    private BigDecimal basePrice;
    private BigDecimal discountPrice;
    private BigDecimal discountPercentage;
    private String discountStartDate;
    private String discountEndDate;
    private Boolean isDiscountActive;
    private Boolean isCurrentlyActive;
    private BigDecimal effectivePrice;
    private BigDecimal perPersonPrice;
    private Integer sofaStandardSetSize;
    private Boolean isCustomPriced;
    
    public ServicePricingResponse(ServicePricing pricing) {
        this.id = pricing.getId();
        this.serviceType = pricing.getServiceType();
        this.serviceTypeKurdish = pricing.getServiceTypeKurdish();
        this.pricingUnit = pricing.getPricingUnit().name();
        this.basePrice = pricing.getBasePrice();
        this.discountPrice = pricing.getDiscountPrice();
        this.discountPercentage = pricing.getDiscountPercentage();
        this.discountStartDate = pricing.getDiscountStartDate() != null ? 
            pricing.getDiscountStartDate().toString() : null;
        this.discountEndDate = pricing.getDiscountEndDate() != null ? 
            pricing.getDiscountEndDate().toString() : null;
        this.isDiscountActive = pricing.getIsDiscountActive();
        this.isCurrentlyActive = pricing.isDiscountCurrentlyActive();
        this.effectivePrice = pricing.isDiscountCurrentlyActive() ? 
            pricing.getDiscountPrice() : pricing.getBasePrice();
        this.perPersonPrice = pricing.getPerPersonPrice();
        this.sofaStandardSetSize = pricing.getSofaStandardSetSize();
        this.isCustomPriced = pricing.getIsCustomPriced();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    
    public String getServiceTypeKurdish() { return serviceTypeKurdish; }
    public void setServiceTypeKurdish(String serviceTypeKurdish) { this.serviceTypeKurdish = serviceTypeKurdish; }
    
    public String getPricingUnit() { return pricingUnit; }
    public void setPricingUnit(String pricingUnit) { this.pricingUnit = pricingUnit; }
    
    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
    
    public BigDecimal getDiscountPrice() { return discountPrice; }
    public void setDiscountPrice(BigDecimal discountPrice) { this.discountPrice = discountPrice; }
    
    public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public void setDiscountPercentage(BigDecimal discountPercentage) { this.discountPercentage = discountPercentage; }
    
    public String getDiscountStartDate() { return discountStartDate; }
    public void setDiscountStartDate(String discountStartDate) { this.discountStartDate = discountStartDate; }
    
    public String getDiscountEndDate() { return discountEndDate; }
    public void setDiscountEndDate(String discountEndDate) { this.discountEndDate = discountEndDate; }
    
    public Boolean getIsDiscountActive() { return isDiscountActive; }
    public void setIsDiscountActive(Boolean isDiscountActive) { this.isDiscountActive = isDiscountActive; }
    
    public Boolean getIsCurrentlyActive() { return isCurrentlyActive; }
    public void setIsCurrentlyActive(Boolean isCurrentlyActive) { this.isCurrentlyActive = isCurrentlyActive; }
    
    public BigDecimal getEffectivePrice() { return effectivePrice; }
    public void setEffectivePrice(BigDecimal effectivePrice) { this.effectivePrice = effectivePrice; }
    
    public BigDecimal getPerPersonPrice() { return perPersonPrice; }
    public void setPerPersonPrice(BigDecimal perPersonPrice) { this.perPersonPrice = perPersonPrice; }
    
    public Integer getSofaStandardSetSize() { return sofaStandardSetSize; }
    public void setSofaStandardSetSize(Integer sofaStandardSetSize) { this.sofaStandardSetSize = sofaStandardSetSize; }
    
    public Boolean getIsCustomPriced() { return isCustomPriced; }
    public void setIsCustomPriced(Boolean isCustomPriced) { this.isCustomPriced = isCustomPriced; }
}
