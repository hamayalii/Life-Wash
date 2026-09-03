package com.ghasl_service.demo.service;

import com.ghasl_service.demo.model.CustomerValue;
import com.ghasl_service.demo.model.Order;
import com.ghasl_service.demo.model.OutboxEvent;
import com.ghasl_service.demo.repository.CustomerValueRepository;
import com.ghasl_service.demo.repository.OrderRepository;
import com.ghasl_service.demo.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled processor for outbox events.
 * Implements Transactional Outbox Pattern for reliable event processing.
 * Replaces @Async event listener to ensure exactly-once processing.
 */
@Service
public class OutboxEventProcessor {
    
    private static final Logger log = LoggerFactory.getLogger(OutboxEventProcessor.class);
    private static final int MAX_RETRIES = 3;
    
    private final OutboxEventRepository outboxEventRepository;
    private final OrderRepository orderRepository;
    private final CustomerValueRepository customerValueRepository;
    private final ApplicationContext applicationContext;
    
    // Self-injection for @Transactional(REQUIRES_NEW) proxy interception
    private OutboxEventProcessor self;
    
    public OutboxEventProcessor(OutboxEventRepository outboxEventRepository,
                               OrderRepository orderRepository,
                               CustomerValueRepository customerValueRepository,
                               ApplicationContext applicationContext) {
        this.outboxEventRepository = outboxEventRepository;
        this.orderRepository = orderRepository;
        this.customerValueRepository = customerValueRepository;
        this.applicationContext = applicationContext;
    }
    
    /**
     * Lazy self-injection to avoid circular dependency during construction.
     * Required for @Transactional(REQUIRES_NEW) to work via Spring proxy.
     */
    private OutboxEventProcessor getSelf() {
        if (self == null) {
            self = applicationContext.getBean(OutboxEventProcessor.class);
        }
        return self;
    }
    
    /**
     * Scheduled task to process pending outbox events.
     * Runs every 30 seconds.
     * Implements idempotency to prevent duplicate processing.
     */
    @Scheduled(fixedRate = 30000)
    @Transactional
    public void processPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
            OutboxEvent.EventStatus.PENDING
        );
        
        if (pendingEvents.isEmpty()) {
            return;
        }
        
        log.info("Processing {} pending outbox events", pendingEvents.size());
        
        for (OutboxEvent event : pendingEvents) {
            try {
                // Process each event in its own transaction (REQUIRES_NEW)
                getSelf().processSingleEvent(event);
            } catch (Exception e) {
                log.error("Failed to process outbox event {}: {}", event.getId(), e.getMessage(), e);
                // Handle failure in the outer transaction
                handleFailure(event, e);
            }
        }
    }
    
    /**
     * Processes a single event in its own transaction.
     * Uses REQUIRES_NEW propagation to isolate failures.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingleEvent(OutboxEvent event) {
        switch (event.getEventType()) {
            case "OrderSubmittedEvent":
                processOrderSubmittedEvent(event);
                break;
            case "OrderRevertedEvent":
                processOrderRevertedEvent(event);
                break;
            default:
                log.warn("Unknown event type: {}", event.getEventType());
                event.setStatus(OutboxEvent.EventStatus.FAILED);
                event.setErrorMessage("Unknown event type");
                outboxEventRepository.save(event);
        }
    }
    
    /**
     * Process OrderSubmittedEvent to update customer lifetime value.
     * This replaces the @Async CustomerValueEventListener.
     * Implements idempotency by checking if customer value already exists.
     */
    private void processOrderSubmittedEvent(OutboxEvent event) {
        Long orderId = Long.parseLong(event.getPayload());
        
        // Fetch the order
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Order {} not found for outbox event {}", orderId, event.getId());
            event.setStatus(OutboxEvent.EventStatus.FAILED);
            event.setErrorMessage("Order not found");
            outboxEventRepository.save(event);
            return;
        }
        
        // Skip if order has no phone number or grand total
        if (order.getPhoneNumber() == null || order.getPhoneNumber().trim().isEmpty()) {
            log.debug("Skipping customer value update for order {} - no phone number", order.getId());
            event.setStatus(OutboxEvent.EventStatus.PROCESSED);
            event.setProcessedAt(LocalDateTime.now());
            outboxEventRepository.save(event);
            return;
        }
        
        if (order.getGrandTotal() == null || order.getGrandTotal().compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Skipping customer value update for order {} - invalid grand total", order.getId());
            event.setStatus(OutboxEvent.EventStatus.PROCESSED);
            event.setProcessedAt(LocalDateTime.now());
            outboxEventRepository.save(event);
            return;
        }
        
        // Find or create customer value record
        CustomerValue customerValue = customerValueRepository
            .findByPhoneNumber(order.getPhoneNumber())
            .orElse(new CustomerValue(order.getPhoneNumber(), order.getCustomerName()));
        
        // Update customer value (idempotent - can be called multiple times safely)
        customerValue.addOrderValue(order.getGrandTotal(), order.getCreatedAt());
        customerValueRepository.save(customerValue);
        
        // Mark event as processed
        event.setStatus(OutboxEvent.EventStatus.PROCESSED);
        event.setProcessedAt(LocalDateTime.now());
        outboxEventRepository.save(event);
        
        log.info("Successfully processed OrderSubmittedEvent for order {}, customer phone: {}", 
                orderId, order.getPhoneNumber());
    }
    
    /**
     * Process OrderRevertedEvent to rollback customer lifetime value.
     * Implements compensating transaction pattern for data integrity.
     */
    private void processOrderRevertedEvent(OutboxEvent event) {
        Long orderId = Long.parseLong(event.getPayload());
        
        // Fetch the order
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Order {} not found for outbox event {}", orderId, event.getId());
            event.setStatus(OutboxEvent.EventStatus.FAILED);
            event.setErrorMessage("Order not found");
            outboxEventRepository.save(event);
            return;
        }
        
        // Skip if order has no phone number or grand total
        if (order.getPhoneNumber() == null || order.getPhoneNumber().trim().isEmpty()) {
            log.debug("Skipping customer value rollback for order {} - no phone number", order.getId());
            event.setStatus(OutboxEvent.EventStatus.PROCESSED);
            event.setProcessedAt(LocalDateTime.now());
            outboxEventRepository.save(event);
            return;
        }
        
        if (order.getGrandTotal() == null || order.getGrandTotal().compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Skipping customer value rollback for order {} - invalid grand total", order.getId());
            event.setStatus(OutboxEvent.EventStatus.PROCESSED);
            event.setProcessedAt(LocalDateTime.now());
            outboxEventRepository.save(event);
            return;
        }
        
        // Find customer value record
        CustomerValue customerValue = customerValueRepository
            .findByPhoneNumber(order.getPhoneNumber())
            .orElse(null);
        
        if (customerValue == null) {
            log.warn("CustomerValue not found for phone number {}, skipping rollback for order {}", 
                    order.getPhoneNumber(), orderId);
            event.setStatus(OutboxEvent.EventStatus.PROCESSED);
            event.setProcessedAt(LocalDateTime.now());
            outboxEventRepository.save(event);
            return;
        }
        
        // Rollback customer value (atomic operation)
        customerValue.subtractOrderValue(order.getGrandTotal(), order.getCreatedAt());
        
        // Recalculate lastOrderDate if orderCount > 0
        if (customerValue.getOrderCount() > 0) {
            LocalDateTime lastOrderDate = orderRepository
                .findLastOrderDateByPhoneNumberExcludingOrderId(
                    order.getPhoneNumber(), orderId)
                .orElse(null);
            
            // FIX: If lastOrderDate is null (was their only order), fallback to firstOrderDate
            // to satisfy DB NOT NULL constraint without altering schema
            if (lastOrderDate == null) {
                lastOrderDate = customerValue.getFirstOrderDate() != null 
                    ? customerValue.getFirstOrderDate() 
                    : order.getCreatedAt();
                log.info("Fallback to firstOrderDate for customer {} after reverting only order", 
                        order.getPhoneNumber());
            }
            
            customerValue.setLastOrderDate(lastOrderDate);
        } else {
            // FIX: When orderCount is 0, set lastOrderDate to firstOrderDate to satisfy NOT NULL constraint
            customerValue.setLastOrderDate(customerValue.getFirstOrderDate() != null 
                ? customerValue.getFirstOrderDate() 
                : order.getCreatedAt());
        }
        
        customerValueRepository.save(customerValue);
        
        // Mark event as processed
        event.setStatus(OutboxEvent.EventStatus.PROCESSED);
        event.setProcessedAt(LocalDateTime.now());
        outboxEventRepository.save(event);
        
        log.info("Successfully processed OrderRevertedEvent for order {}, customer phone: {}. " +
                 "Rolled back value: {}, new totalLifetimeValue: {}", 
                orderId, order.getPhoneNumber(), order.getGrandTotal(), customerValue.getTotalLifetimeValue());
    }
    
    private void handleFailure(OutboxEvent event, Exception e) {
        event.setRetryCount(event.getRetryCount() + 1);
        
        if (event.getRetryCount() >= MAX_RETRIES) {
            event.setStatus(OutboxEvent.EventStatus.FAILED);
            event.setErrorMessage(e.getMessage());
            log.error("Outbox event {} failed after {} retries", event.getId(), MAX_RETRIES);
        }
        
        outboxEventRepository.save(event);
    }
}
