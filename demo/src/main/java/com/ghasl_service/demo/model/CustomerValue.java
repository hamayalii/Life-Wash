package com.ghasl_service.demo.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing cumulative customer lifetime value for CLV calculations.
 * Tracks total revenue per customer for marketing ROI analysis.
 * Updated via Spring Events (OrderCreatedEvent) - NO database triggers.
 */
@Entity
@Table(name = "customer_value")
public class CustomerValue {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String phoneNumber;
    
    @Column(nullable = false)
    private String customerName;
    
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalLifetimeValue;
    
    @Column(nullable = false)
    private Integer orderCount;
    
    @Column(nullable = false)
    private LocalDateTime firstOrderDate;
    
    @Column(nullable = false)
    private LocalDateTime lastOrderDate;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public CustomerValue() {
        this.totalLifetimeValue = BigDecimal.ZERO;
        this.orderCount = 0;
    }
    
    public CustomerValue(String phoneNumber, String customerName) {
        this();
        this.phoneNumber = phoneNumber;
        this.customerName = customerName;
    }
    
    // Getters and Setters
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    
    public String getCustomerName() {
        return customerName;
    }
    
    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }
    
    public BigDecimal getTotalLifetimeValue() {
        return totalLifetimeValue;
    }
    
    public void setTotalLifetimeValue(BigDecimal totalLifetimeValue) {
        this.totalLifetimeValue = totalLifetimeValue;
    }
    
    public Integer getOrderCount() {
        return orderCount;
    }
    
    public void setOrderCount(Integer orderCount) {
        this.orderCount = orderCount;
    }
    
    public LocalDateTime getFirstOrderDate() {
        return firstOrderDate;
    }
    
    public void setFirstOrderDate(LocalDateTime firstOrderDate) {
        this.firstOrderDate = firstOrderDate;
    }
    
    public LocalDateTime getLastOrderDate() {
        return lastOrderDate;
    }
    
    public void setLastOrderDate(LocalDateTime lastOrderDate) {
        this.lastOrderDate = lastOrderDate;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    /**
     * Helper method to add order value to customer lifetime value.
     * Used by CustomerValueEventListener.
     */
    public void addOrderValue(BigDecimal orderValue, LocalDateTime orderDate) {
        this.totalLifetimeValue = this.totalLifetimeValue.add(orderValue);
        this.orderCount = this.orderCount + 1;
        this.lastOrderDate = orderDate;
        
        if (this.firstOrderDate == null || orderDate.isBefore(this.firstOrderDate)) {
            this.firstOrderDate = orderDate;
        }
    }
    
    /**
     * Subtracts order value from customer lifetime value (compensating transaction).
     * Used when an order is reverted from ACCEPTED to PENDING.
     * Note: lastOrderDate recalculation is handled in the service layer for better separation of concerns.
     * 
     * @param amount The order grand total to subtract
     * @param orderCreatedAt The order creation timestamp (not used directly, kept for API consistency)
     */
    public void subtractOrderValue(BigDecimal amount, LocalDateTime orderCreatedAt) {
        // Deduct from total lifetime value (with floor at 0)
        BigDecimal newTotal = this.totalLifetimeValue.subtract(amount);
        this.totalLifetimeValue = newTotal.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newTotal;
        
        // Decrement order count (with floor at 0)
        this.orderCount = Math.max(0, this.orderCount - 1);
        
        // Note: lastOrderDate recalculation is handled in OutboxEventProcessor
        // by querying OrderRepository for the max timestamp of remaining orders
    }
}
