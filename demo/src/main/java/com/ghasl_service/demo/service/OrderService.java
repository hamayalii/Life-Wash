package com.ghasl_service.demo.service;

import com.ghasl_service.demo.event.OrderSubmittedEvent;
import com.ghasl_service.demo.exception.OptimisticLockConflictException;
import com.ghasl_service.demo.model.Order;
import com.ghasl_service.demo.model.OutboxEvent;
import com.ghasl_service.demo.model.RejectionReason;
import com.ghasl_service.demo.repository.OrderRepository;
import com.ghasl_service.demo.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventRepository outboxEventRepository;

    public OrderService(OrderRepository orderRepository, ApplicationEventPublisher eventPublisher, OutboxEventRepository outboxEventRepository) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public Order createOrder(Order order) {
        log.info("Saving new order for customer: {}", order.getCustomerName());
        Order savedOrder = orderRepository.save(order);
        
        log.info("Publishing OrderSubmittedEvent for order ID: {}", savedOrder.getId());
        eventPublisher.publishEvent(new OrderSubmittedEvent(this, savedOrder));
        
        return savedOrder;
    }

    @Transactional
    public Order updateOrderPrice(Long orderId, java.math.BigDecimal newPrice) {
        try {
            if (newPrice == null || newPrice.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("newPrice must be greater than zero");
            }
            Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));
            
            log.info("Updating price for order {}: {} -> {}", orderId, order.getPrice(), newPrice);
            order.setPrice(newPrice);
            // MUST use saveAndFlush to trigger DB version check immediately inside try block
            return orderRepository.saveAndFlush(order);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException("ئەم داواکارییە پێشتر لەسەر سیستەمەکە دەستکاری کراوە.");
        }
    }

    /**
     * Marks an order as ACCEPTED. Idempotent: if already ACCEPTED, throws
     * IllegalStateException with a Kurdish message stating the current status.
     */
    @Transactional
    public Order acceptOrder(Long orderId) {
        try {
            Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

            if (order.getWorkStatus() == Order.WorkStatus.ACCEPTED) {
                throw new IllegalStateException("ئەم داواکارییە پێشتر وەرگیراوە ✅");
            }
            if (order.getWorkStatus() == Order.WorkStatus.REJECTED) {
                throw new IllegalStateException("ئەم داواکارییە پێشتر ڕەتکراوەتەوە ❌ — دەتوانیت دووبارە بگۆڕیتەوە");
            }

            order.setWorkStatus(Order.WorkStatus.ACCEPTED);
            log.info("AUDIT: Order #{} ACCEPTED", orderId);
            // MUST use saveAndFlush to trigger DB version check immediately inside try block
            return orderRepository.saveAndFlush(order);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException("ئەم داواکارییە پێشتر لەسەر سیستەمەکە دەستکاری کراوە.");
        }
    }

    /**
     * Marks an order as REJECTED. Idempotent: if already REJECTED, throws
     * IllegalStateException with a Kurdish message stating the current status.
     */
    @Transactional
    public Order rejectOrder(Long orderId) {
        try {
            Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

            if (order.getWorkStatus() == Order.WorkStatus.REJECTED) {
                throw new IllegalStateException("ئەم داواکارییە پێشتر ڕەتکراوەتەوە ❌");
            }
            if (order.getWorkStatus() == Order.WorkStatus.ACCEPTED) {
                throw new IllegalStateException("ئەم داواکارییە پێشتر وەرگیراوە ✅ — دەتوانیت دووبارە بگۆڕیتەوە");
            }

            order.setWorkStatus(Order.WorkStatus.REJECTED);
            log.info("AUDIT: Order #{} REJECTED", orderId);
            // MUST use saveAndFlush to trigger DB version check immediately inside try block
            return orderRepository.saveAndFlush(order);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException("ئەم داواکارییە پێشتر لەسەر سیستەمەکە دەستکاری کراوە.");
        }
    }

    /**
     * Marks an order as REJECTED with a specific reason.
     * This is the preferred method for rejection as it tracks why the order was rejected.
     * Allows transition from ACCEPTED to REJECTED for POS workflow.
     */
    @Transactional
    public Order rejectOrderWithReason(Long orderId, RejectionReason rejectionReason) {
        try {
            Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

            if (order.getWorkStatus() == Order.WorkStatus.REJECTED) {
                throw new IllegalStateException("ئەم داواکارییە پێشتر ڕەتکراوەتەوە ❌");
            }

            order.setWorkStatus(Order.WorkStatus.REJECTED);
            order.setRejectionReason(rejectionReason);
            log.info("AUDIT: Order #{} REJECTED with reason: {} (previous status: {})", orderId, rejectionReason, order.getWorkStatus());
            // MUST use saveAndFlush to trigger DB version check immediately inside try block
            return orderRepository.saveAndFlush(order);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException("ئەم داواکارییە پێشتر لەسەر سیستەمەکە دەستکاری کراوە.");
        }
    }

    /**
     * Reverts an order from ACCEPTED or REJECTED to PENDING status.
     * Creates a compensating event for CustomerValue rollback.
     * Uses optimistic locking to prevent concurrent modification conflicts.
     * 
     * @param orderId The order ID to revert
     * @return The updated Order entity
     * @throws OptimisticLockConflictException if concurrent modification detected
     * @throws IllegalArgumentException if order is not in ACCEPTED or REJECTED status
     */
    @Transactional
    public Order revertOrder(Long orderId) {
        log.info("Reverting order {} from ACCEPTED/REJECTED to PENDING", orderId);
        
        try {
            Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
            
            // Validate current status - allow both ACCEPTED and REJECTED
            if (order.getWorkStatus() != Order.WorkStatus.ACCEPTED && order.getWorkStatus() != Order.WorkStatus.REJECTED) {
                throw new IllegalArgumentException(
                    "Order must be in ACCEPTED or REJECTED status to revert. Current status: " + order.getWorkStatus());
            }
            
            // Update order status
            order.setWorkStatus(Order.WorkStatus.PENDING);
            orderRepository.save(order);
            
            // Create compensating event for CustomerValue rollback
            OutboxEvent outboxEvent = new OutboxEvent("OrderRevertedEvent", String.valueOf(orderId));
            outboxEventRepository.save(outboxEvent);
            
            log.info("Order {} reverted to PENDING, compensating event created: {}", 
                    orderId, outboxEvent.getId());
            
            return order;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException("ئەم داواکارییە پێشتر لەسەر سیستەمەکە دەستکاری کراوە.");
        }
    }
}
