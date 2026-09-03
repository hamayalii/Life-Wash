package com.ghasl_service.demo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Outbox Event entity for Transactional Outbox Pattern.
 * Events are saved in the same transaction as the order creation.
 * A scheduled processor polls and processes pending events.
 * This ensures reliable event processing even if the server crashes.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    
    public enum EventStatus {
        PENDING,
        PROCESSED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String eventType;
    private String payload;
    
    @Enumerated(EnumType.STRING)
    private EventStatus status = EventStatus.PENDING;
    
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private Integer retryCount = 0;
    private String errorMessage;

    public OutboxEvent() {
        this.createdAt = LocalDateTime.now();
    }

    public OutboxEvent(String eventType, String payload) {
        this();
        this.eventType = eventType;
        this.payload = payload;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    
    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus status) { this.status = status; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
    
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
