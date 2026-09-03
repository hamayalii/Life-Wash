package com.ghasl_service.demo.dto;

import java.math.BigDecimal;

public class NewServiceRequest {
    private String kurdishName;
    private String englishName;
    private BigDecimal basePrice;
    private String pricingUnit;
    private Integer sofaStandardSetSize;
    private Boolean isCustomPriced;
    
    // Getters and Setters
    public String getKurdishName() { return kurdishName; }
    public void setKurdishName(String kurdishName) { this.kurdishName = kurdishName; }
    
    public String getEnglishName() { return englishName; }
    public void setEnglishName(String englishName) { this.englishName = englishName; }
    
    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
    
    public String getPricingUnit() { return pricingUnit; }
    public void setPricingUnit(String pricingUnit) { this.pricingUnit = pricingUnit; }
    
    public Integer getSofaStandardSetSize() { return sofaStandardSetSize; }
    public void setSofaStandardSetSize(Integer sofaStandardSetSize) { this.sofaStandardSetSize = sofaStandardSetSize; }
    
    public Boolean getIsCustomPriced() { return isCustomPriced; }
    public void setIsCustomPriced(Boolean isCustomPriced) { this.isCustomPriced = isCustomPriced; }
}
