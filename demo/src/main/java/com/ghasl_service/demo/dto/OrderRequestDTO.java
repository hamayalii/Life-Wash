package com.ghasl_service.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for creating POS orders.
 * Contains customer information, operator tracking, and list of order items.
 */
public class OrderRequestDTO {

    @NotBlank(message = "customerName is required")
    @Size(max = 100, message = "customerName must not exceed 100 characters")
    private String customerName;

    @NotBlank(message = "phoneNumber is required")
    @Size(max = 20, message = "phoneNumber must not exceed 20 characters")
    private String phoneNumber;

    @Size(max = 255, message = "address must not exceed 255 characters")
    private String address;

    @Size(max = 1000, message = "notes must not exceed 1000 characters")
    private String notes;

    /**
     * Operator/employee who created this order. Required for auditing.
     * Anonymous orders are strictly prohibited.
     */
    @NotBlank(message = "createdBy is required for auditing")
    @Size(max = 50, message = "createdBy must not exceed 50 characters")
    private String createdBy;

    /**
     * Idempotency key for preventing duplicate order submissions.
     * Ensures that rapid double-clicks or network retries don't create duplicate orders.
     * Frontend must generate a UUID for each order attempt and regenerate on successful response.
     */
    @NotBlank(message = "idempotencyKey is required for duplicate prevention")
    @Size(max = 36, message = "idempotencyKey must be a valid UUID")
    private String idempotencyKey;

    /**
     * List of line items in this order.
     * Backend will recalculate totals from verified database prices.
     */
    @NotNull(message = "items list is required")
    @Size(min = 1, message = "Order must contain at least one item")
    @Valid
    private List<OrderItemDTO> items;

    public OrderRequestDTO() {
    }

    public OrderRequestDTO(String customerName, String phoneNumber, String address, 
                          String notes, String createdBy, String idempotencyKey, List<OrderItemDTO> items) {
        this.customerName = customerName;
        this.phoneNumber = phoneNumber;
        this.address = address;
        this.notes = notes;
        this.createdBy = createdBy;
        this.idempotencyKey = idempotencyKey;
        this.items = items;
    }

    // Getters and Setters

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public List<OrderItemDTO> getItems() {
        return items;
    }

    public void setItems(List<OrderItemDTO> items) {
        this.items = items;
    }
}
