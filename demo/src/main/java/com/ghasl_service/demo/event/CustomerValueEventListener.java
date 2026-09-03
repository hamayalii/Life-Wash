package com.ghasl_service.demo.event;

import com.ghasl_service.demo.model.CustomerValue;
import com.ghasl_service.demo.model.Order;
import com.ghasl_service.demo.repository.CustomerValueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

/**
 * Event listener for updating CustomerValue table when orders are created.
 * 
 * DEPRECATED: This listener has been replaced by the Transactional Outbox Pattern.
 * The OutboxEventProcessor now handles customer value updates reliably.
 * 
 * This class is kept for backward compatibility but is no longer used.
 * The @Async annotation has been removed to prevent duplicate processing.
 */
@Component
@Deprecated
public class CustomerValueEventListener {

    private static final Logger log = LoggerFactory.getLogger(CustomerValueEventListener.class);

    private final CustomerValueRepository customerValueRepository;

    public CustomerValueEventListener(CustomerValueRepository customerValueRepository) {
        this.customerValueRepository = customerValueRepository;
    }

    /**
     * Handles OrderSubmittedEvent to update customer lifetime value.
     * 
     * CRITICAL FIX: Changed from @EventListener to @TransactionalEventListener(phase = BEFORE_COMMIT)
     * to ensure ACID transaction boundaries. Customer value aggregation is now mathematically
     * bound to the same database transaction as order creation, preventing data divergence
     * if the server crashes after order commit but before async processing completes.
     * 
     * This listener is responsible for adding customer value via OrderSubmittedEvent.
     * The OutboxEventProcessor handles OrderRevertedEvent separately.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleOrderSubmittedEvent(OrderSubmittedEvent event) {
        Order order = event.getOrder();
        
        log.info("Processing OrderSubmittedEvent for customer: {}, orderTotal: {}", 
                order.getPhoneNumber(), order.getGrandTotal());
        
        // Update customer lifetime value
        CustomerValue customerValue = customerValueRepository
            .findByPhoneNumber(order.getPhoneNumber())
            .orElse(new CustomerValue());
        
        // Initialize new customer record if needed
        if (customerValue.getId() == null) {
            customerValue.setPhoneNumber(order.getPhoneNumber());
            customerValue.setCustomerName(order.getCustomerName());
            customerValue.setTotalLifetimeValue(BigDecimal.ZERO);
            customerValue.setOrderCount(0);
            customerValue.setFirstOrderDate(order.getCreatedAt());
        }
        
        // Add order value to customer lifetime value
        BigDecimal currentValue = customerValue.getTotalLifetimeValue() != null 
            ? customerValue.getTotalLifetimeValue() 
            : BigDecimal.ZERO;
        BigDecimal orderValue = order.getGrandTotal() != null 
            ? order.getGrandTotal() 
            : BigDecimal.ZERO;
        
        customerValue.setTotalLifetimeValue(currentValue.add(orderValue));
        customerValue.setOrderCount(customerValue.getOrderCount() + 1);
        customerValue.setLastOrderDate(order.getCreatedAt());
        
        customerValueRepository.save(customerValue);
        
        log.info("Updated customer value for phoneNumber: {}, newTotal: {}, orderCount: {}", 
                order.getPhoneNumber(), customerValue.getTotalLifetimeValue(), customerValue.getOrderCount());
    }
}
