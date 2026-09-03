package com.ghasl_service.demo.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.PrePersist;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_items")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    @JsonBackReference
    private Order order;

    @ManyToOne
    @JoinColumn(name = "service_id", nullable = false)
    @JsonIgnore
    private Service service;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private ServiceCategory serviceCategory;

    /**
     * Quantity of units for this line item (e.g., 3.5 for square meters, 2 for pieces).
     */
    private BigDecimal quantity;

    /**
     * Unit type for this specific line item using the global MeasurementUnit enum.
     * Allows override of the service's default unit type if needed.
     * This ensures type safety and historical data integrity.
     */
    @Enumerated(EnumType.STRING)
    private MeasurementUnit unitName;

    /**
     * Unit price at the time of order. This is the price per unit used for calculation.
     * For services with negotiated prices, this is the manually entered unit price.
     */
    private BigDecimal unitPrice;

    /**
     * Total price for this line item (quantity * unitPrice).
     * Stored for audit trail and performance; backend recalculates for verification.
     */
    private BigDecimal totalPrice;

    // Locked pricing fields for historical integrity
    @Column(name = "locked_price", precision = 10, scale = 2)
    private BigDecimal lockedPrice;

    @Column(name = "locked_discount", precision = 10, scale = 2)
    private BigDecimal lockedDiscount;

    @Column(name = "locked_discount_percentage", precision = 5, scale = 2)
    private BigDecimal lockedDiscountPercentage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public OrderItem() {
    }

    public OrderItem(Order order, Service service, BigDecimal quantity, MeasurementUnit unitName, 
                     BigDecimal unitPrice, BigDecimal totalPrice) {
        this.order = order;
        this.service = service;
        this.quantity = quantity;
        this.unitName = unitName;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public MeasurementUnit getUnitName() {
        return unitName;
    }

    public void setUnitName(MeasurementUnit unitName) {
        this.unitName = unitName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public ServiceCategory getServiceCategory() {
        return serviceCategory;
    }

    public void setServiceCategory(ServiceCategory serviceCategory) {
        this.serviceCategory = serviceCategory;
    }

    public BigDecimal getLockedPrice() {
        return lockedPrice;
    }

    public void setLockedPrice(BigDecimal lockedPrice) {
        this.lockedPrice = lockedPrice;
    }

    public BigDecimal getLockedDiscount() {
        return lockedDiscount;
    }

    public void setLockedDiscount(BigDecimal lockedDiscount) {
        this.lockedDiscount = lockedDiscount;
    }

    public BigDecimal getLockedDiscountPercentage() {
        return lockedDiscountPercentage;
    }

    public void setLockedDiscountPercentage(BigDecimal lockedDiscountPercentage) {
        this.lockedDiscountPercentage = lockedDiscountPercentage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
