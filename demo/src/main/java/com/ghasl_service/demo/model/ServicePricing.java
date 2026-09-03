package com.ghasl_service.demo.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_pricing")
public class ServicePricing {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private ServiceCategory serviceCategory;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MeasurementUnit pricingUnit;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;
    
    // Discount fields
    @Column(name = "discount_price", precision = 10, scale = 2)
    private BigDecimal discountPrice;
    
    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;
    
    @Column(name = "discount_start_date")
    private LocalDateTime discountStartDate;
    
    @Column(name = "discount_end_date")
    private LocalDateTime discountEndDate;
    
    @Column(name = "is_discount_active")
    private Boolean isDiscountActive = false;
    
    // Sofa-specific: standard set size for per-person calculation
    @Column(name = "sofa_standard_set_size")
    private Integer sofaStandardSetSize = 10;
    
    // Custom pricing flag for services with variable/on-site pricing
    @Column(name = "is_custom_priced")
    private Boolean isCustomPriced = false;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public ServiceCategory getServiceCategory() { return serviceCategory; }
    public void setServiceCategory(ServiceCategory serviceCategory) { this.serviceCategory = serviceCategory; }
    
    // Convenience method for backward compatibility
    public String getServiceType() {
        return serviceCategory != null ? serviceCategory.getEnglishName() : null;
    }
    
    public String getServiceTypeKurdish() {
        return serviceCategory != null ? serviceCategory.getKurdishName() : null;
    }
    
    public MeasurementUnit getPricingUnit() { return pricingUnit; }
    public void setPricingUnit(MeasurementUnit pricingUnit) { this.pricingUnit = pricingUnit; }
    
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
    
    public Boolean getIsCustomPriced() { return isCustomPriced; }
    public void setIsCustomPriced(Boolean isCustomPriced) { this.isCustomPriced = isCustomPriced; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    // Business logic method to check if discount is currently active
    public boolean isDiscountCurrentlyActive() {
        if (!isDiscountActive || discountPrice == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return (discountStartDate == null || !now.isBefore(discountStartDate)) &&
               (discountEndDate == null || !now.isAfter(discountEndDate));
    }
    
    // Calculate per-person price for sofa
    public BigDecimal getPerPersonPrice() {
        if (serviceCategory != null && "SOFA".equals(serviceCategory.getEnglishName()) 
            && sofaStandardSetSize != null && sofaStandardSetSize > 0) {
            BigDecimal effectivePrice = isDiscountCurrentlyActive() ? discountPrice : basePrice;
            return effectivePrice.divide(BigDecimal.valueOf(sofaStandardSetSize), 2, RoundingMode.HALF_UP);
        }
        return null;
    }
}
