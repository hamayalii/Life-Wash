package com.ghasl_service.demo.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Order {

    /** Work-acceptance status driven by the admin via Telegram inline buttons. */
    public enum WorkStatus {
        PENDING,   // default — order received, not yet acted on
        ACCEPTED,  // admin confirmed the work will proceed
        REJECTED   // admin declined (excluded from revenue)
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    private String customerName;
    private String phoneNumber;
    private String rugType;

    /**
     * Null when pricing is pending admin confirmation (e.g. wool sofa sets where
     * fabric/soiling affects the final charge). Never null for metered types once
     * a quantity is provided.
     * @deprecated Use grandTotal from order items instead for POS orders
     */
    @Deprecated
    private BigDecimal price;

    private LocalDateTime createdAt;

    /** Carried from the lead form into the order record. */
    private String address;
    
    /**
     * Customer notes/message for the order.
     * Mapped with TEXT column definition to allow longer notes safely.
     */
    @Column(columnDefinition = "TEXT")
    private String message;

    /**
     * Number of units (metres for persian/shag, pieces for silk/synthetic,
     * seats for wool). Null when the rugType is antique (no order created)
     * or when the frontend did not send a quantity.
     * @deprecated Use quantity in OrderItem instead for POS orders
     */
    @Deprecated
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    private WorkStatus workStatus = WorkStatus.PENDING;

    /**
     * Reason why the order was rejected (if applicable).
     * NULL for non-rejected orders or historical orders rejected before this feature.
     */
    @Enumerated(EnumType.STRING)
    private RejectionReason rejectionReason;

    /**
     * Source/channel where the order originated (WEB, POS, TELEGRAM_BOT).
     * Replaces fragile magic strings in createdBy field.
     */
    @Enumerated(EnumType.STRING)
    private OrderSource orderSource;

    /**
     * Operator/employee who created this order. Required for auditing and accountability.
     * Anonymous orders are strictly prohibited.
     */
    private String createdBy;

    /**
     * Idempotency key for preventing duplicate order submissions.
     * Ensures that rapid double-clicks or network retries don't create duplicate orders.
     * Unique constraint enforced at database level.
     */
    @Column(unique = true)
    private String idempotencyKey;

    /**
     * Grand total of all order items. Recalculated by backend from verified database prices.
     * Never trust frontend calculations for financial data.
     */
    private BigDecimal grandTotal;

    /**
     * List of line items in this order. Supports multi-item POS orders.
     * Cascade ALL ensures child items are persisted/removed with the parent.
     * @JsonManagedReference marks this as the parent side of the bidirectional relationship
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<OrderItem> items = new ArrayList<>();

    public Order() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Backward-compatible 4-arg constructor (kept for any existing callers /
     * tests that do not yet supply address, message, or quantity).
     */
    public Order(String customerName, String phoneNumber, String rugType, BigDecimal price) {
        this.customerName = customerName;
        this.phoneNumber  = phoneNumber;
        this.rugType      = rugType;
        this.price        = price;
        this.createdAt    = LocalDateTime.now();
        this.workStatus   = WorkStatus.PENDING;
    }

    /**
     * Full constructor including all lead-form fields and the resolved quantity.
     * price may be null for pending-admin orders (wool).
     */
    public Order(String customerName, String phoneNumber, String rugType,
                 BigDecimal price, String address, String message, BigDecimal quantity) {
        this.customerName = customerName;
        this.phoneNumber  = phoneNumber;
        this.rugType      = rugType;
        this.price        = price;
        this.address      = address;
        this.message      = message;
        this.quantity     = quantity;
        this.createdAt    = LocalDateTime.now();
        this.workStatus   = WorkStatus.PENDING;
    }

    // ── Getters and Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getRugType() { return rugType; }
    public void setRugType(String rugType) { this.rugType = rugType; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public WorkStatus getWorkStatus() { return workStatus; }
    public void setWorkStatus(WorkStatus workStatus) { this.workStatus = workStatus; }

    public RejectionReason getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(RejectionReason rejectionReason) { this.rejectionReason = rejectionReason; }

    public OrderSource getOrderSource() { return orderSource; }
    public void setOrderSource(OrderSource orderSource) { this.orderSource = orderSource; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

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

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal(BigDecimal grandTotal) {
        this.grandTotal = grandTotal;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    /**
     * Helper method to add an item to the order.
     * Automatically sets the bidirectional relationship.
     */
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    /**
     * Helper method to remove an item from the order.
     * Automatically clears the bidirectional relationship.
     */
    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }
}
