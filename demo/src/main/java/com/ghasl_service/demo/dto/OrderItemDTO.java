package com.ghasl_service.demo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * DTO for individual line items in an order.
 * Used for POS order creation and cart management.
 */
public class OrderItemDTO {

    @NotNull(message = "serviceId is required")
    private Long serviceId;

    @NotNull(message = "quantity is required")
    @DecimalMin(value = "0.01", message = "quantity must be greater than 0")
    private BigDecimal quantity;

    private String unitName;

    @NotNull(message = "unitPrice is required")
    @DecimalMin(value = "0.01", message = "unitPrice must be greater than 0")
    private BigDecimal unitPrice;

    @NotNull(message = "totalPrice is required")
    @DecimalMin(value = "0.01", message = "totalPrice must be greater than 0")
    private BigDecimal totalPrice;

    public OrderItemDTO() {
    }

    public OrderItemDTO(Long serviceId, BigDecimal quantity, String unitName, 
                       BigDecimal unitPrice, BigDecimal totalPrice) {
        this.serviceId = serviceId;
        this.quantity = quantity;
        this.unitName = unitName;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
    }

    // Getters and Setters

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
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
}
