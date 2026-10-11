package com.ghasl_service.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Pattern;
import com.ghasl_service.demo.validation.ValidLocationFields;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * DTO for creating POS orders.
 * Contains customer information, operator tracking, and list of order items.
 */
@ValidLocationFields
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

    /**
     * GPS latitude in decimal degrees (-90 to 90).
     * Optional field.
     */
    @DecimalMin(value = "-90", message = "latitude must be >= -90")
    @DecimalMax(value = "90", message = "latitude must be <= 90")
    private BigDecimal latitude;

    /**
     * GPS longitude in decimal degrees (-180 to 180).
     * Optional field.
     */
    @DecimalMin(value = "-180", message = "longitude must be >= -180")
    @DecimalMax(value = "180", message = "longitude must be <= 180")
    private BigDecimal longitude;

    /**
     * GPS accuracy in meters (0 to 10000).
     * Optional field.
     */
    @DecimalMin(value = "0", message = "accuracy must be >= 0")
    @DecimalMax(value = "10000", message = "accuracy must be <= 10000")
    private Double accuracyMeters;

    /**
     * Source of location data: gps or manual.
     * Optional field.
     */
    @Pattern(regexp = "gps|manual", message = "locationSource must be 'gps' or 'manual'")
    private String locationSource;

    /**
     * Timestamp when location was captured (timezone-aware).
     * Optional field.
     */
    private Instant locationCapturedAt;

    /**
     * Reverse-geocoded address from Nominatim (informational only, not used in exports).
     * Optional field, max 500 chars.
     */
    @Size(max = 500, message = "formattedAddress must not exceed 500 characters")
    private String formattedAddress;

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

    public OrderRequestDTO(String customerName, String phoneNumber, String address, 
                          String notes, String createdBy, String idempotencyKey, List<OrderItemDTO> items,
                          BigDecimal latitude, BigDecimal longitude, Double accuracyMeters,
                          String locationSource, Instant locationCapturedAt, String formattedAddress) {
        this.customerName = customerName;
        this.phoneNumber = phoneNumber;
        this.address = address;
        this.notes = notes;
        this.createdBy = createdBy;
        this.idempotencyKey = idempotencyKey;
        this.items = items;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.locationSource = locationSource;
        this.locationCapturedAt = locationCapturedAt;
        this.formattedAddress = formattedAddress;
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

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public Double getAccuracyMeters() {
        return accuracyMeters;
    }

    public void setAccuracyMeters(Double accuracyMeters) {
        this.accuracyMeters = accuracyMeters;
    }

    public String getLocationSource() {
        return locationSource;
    }

    public void setLocationSource(String locationSource) {
        this.locationSource = locationSource;
    }

    public Instant getLocationCapturedAt() {
        return locationCapturedAt;
    }

    public void setLocationCapturedAt(Instant locationCapturedAt) {
        this.locationCapturedAt = locationCapturedAt;
    }

    public String getFormattedAddress() {
        return formattedAddress;
    }

    public void setFormattedAddress(String formattedAddress) {
        this.formattedAddress = formattedAddress;
    }
}
