package com.ghasl_service.demo.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing monthly marketing/advertising expenses by category and campaign.
 * Tracks marketing spend for ROI calculations and customer acquisition cost analysis.
 * Period is stored as String in "yyyy-MM" format to avoid JPA YearMonth serialization issues.
 */
@Entity
@Table(name = "marketing_spend")
public class MarketingSpend {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 7)
    private String period; // Format: "yyyy-MM"
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarketingChannel channel;
    
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    
    @Column(length = 255)
    private String campaignName;
    
    @Column(length = 500)
    private String description;
    
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
    
    public MarketingSpend() {
    }
    
    public MarketingSpend(String period, MarketingChannel channel, BigDecimal amount) {
        this.period = period;
        this.channel = channel;
        this.amount = amount;
    }
    
    // Getters and Setters
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getPeriod() {
        return period;
    }
    
    public void setPeriod(String period) {
        this.period = period;
    }
    
    public MarketingChannel getChannel() {
        return channel;
    }
    
    public void setChannel(MarketingChannel channel) {
        this.channel = channel;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    
    public String getCampaignName() {
        return campaignName;
    }
    
    public void setCampaignName(String campaignName) {
        this.campaignName = campaignName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
