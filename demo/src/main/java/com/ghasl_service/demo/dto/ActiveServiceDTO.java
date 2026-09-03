package com.ghasl_service.demo.dto;

import com.ghasl_service.demo.model.MeasurementUnit;

import java.math.BigDecimal;

/**
 * Flattened aggregate DTO for active services.
 * This is the Single Source of Truth for all client interfaces (Customer Form, POS, Admin Dashboard, Telegram Bot).
 * 
 * Structure: Π_metadata(Service ⋈ ServiceCategory ⋈ ServicePricing)
 * 
 * Contains:
 * - Service identification (id, names)
 * - Unit type (MeasurementUnit enum)
 * - Pricing (active price with discount calculation done by backend)
 * - Category metadata for UI rendering
 */
public class ActiveServiceDTO {
    
    private Long id;  // Service.id (single source of truth)
    private Long categoryId;  // ServiceCategory.id (for reference)
    private String englishName;
    private String kurdishName;
    private MeasurementUnit measurementUnit;
    private BigDecimal activePrice;
    private BigDecimal discountedPrice;
    private BigDecimal discountPercentage;
    private boolean discountActive;
    private boolean customPriced;
    private Integer sofaStandardSetSize;
    private String iconUrl;
    
    // Constructors
    public ActiveServiceDTO() {}
    
    public ActiveServiceDTO(Long id, Long categoryId, String englishName, String kurdishName, 
                           MeasurementUnit measurementUnit, BigDecimal activePrice,
                           BigDecimal discountedPrice, BigDecimal discountPercentage,
                           boolean discountActive, boolean customPriced,
                           Integer sofaStandardSetSize, String iconUrl) {
        this.id = id;  // Service.id (single source of truth)
        this.categoryId = categoryId;  // ServiceCategory.id (for reference)
        this.englishName = englishName;
        this.kurdishName = kurdishName;
        this.measurementUnit = measurementUnit;
        this.activePrice = activePrice;
        this.discountedPrice = discountedPrice;
        this.discountPercentage = discountPercentage;
        this.discountActive = discountActive;
        this.customPriced = customPriced;
        this.sofaStandardSetSize = sofaStandardSetSize;
        this.iconUrl = iconUrl;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getCategoryId() {
        return categoryId;
    }
    
    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
    
    public String getEnglishName() {
        return englishName;
    }
    
    public void setEnglishName(String englishName) {
        this.englishName = englishName;
    }
    
    public String getKurdishName() {
        return kurdishName;
    }
    
    public void setKurdishName(String kurdishName) {
        this.kurdishName = kurdishName;
    }
    
    public MeasurementUnit getMeasurementUnit() {
        return measurementUnit;
    }
    
    public void setMeasurementUnit(MeasurementUnit measurementUnit) {
        this.measurementUnit = measurementUnit;
    }
    
    public BigDecimal getActivePrice() {
        return activePrice;
    }
    
    public void setActivePrice(BigDecimal activePrice) {
        this.activePrice = activePrice;
    }
    
    public BigDecimal getDiscountedPrice() {
        return discountedPrice;
    }
    
    public void setDiscountedPrice(BigDecimal discountedPrice) {
        this.discountedPrice = discountedPrice;
    }
    
    public BigDecimal getDiscountPercentage() {
        return discountPercentage;
    }
    
    public void setDiscountPercentage(BigDecimal discountPercentage) {
        this.discountPercentage = discountPercentage;
    }
    
    public boolean isDiscountActive() {
        return discountActive;
    }
    
    public void setDiscountActive(boolean discountActive) {
        this.discountActive = discountActive;
    }
    
    public boolean isCustomPriced() {
        return customPriced;
    }
    
    public void setCustomPriced(boolean customPriced) {
        this.customPriced = customPriced;
    }
    
    public Integer getSofaStandardSetSize() {
        return sofaStandardSetSize;
    }
    
    public void setSofaStandardSetSize(Integer sofaStandardSetSize) {
        this.sofaStandardSetSize = sofaStandardSetSize;
    }
    
    public String getIconUrl() {
        return iconUrl;
    }
    
    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }
}
