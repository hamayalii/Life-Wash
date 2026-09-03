package com.ghasl_service.demo.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghasl_service.demo.dto.OrderRequestDTO;
import com.ghasl_service.demo.dto.RejectionReasonResponse;
import com.ghasl_service.demo.dto.RejectionRequest;
import com.ghasl_service.demo.exception.OptimisticLockConflictException;
import com.ghasl_service.demo.model.IdempotencyKey;
import com.ghasl_service.demo.model.Order;
import com.ghasl_service.demo.model.RejectionReason;
import com.ghasl_service.demo.service.IdempotencyService;
import com.ghasl_service.demo.service.OrderManagementService;
import com.ghasl_service.demo.service.OrderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderActionController {

    private static final Logger log = LoggerFactory.getLogger(OrderActionController.class);

    private final OrderService orderService;
    private final OrderManagementService orderManagementService;
    private final com.ghasl_service.demo.repository.OrderRepository orderRepository;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Autowired
    public OrderActionController(OrderService orderService, OrderManagementService orderManagementService,
            com.ghasl_service.demo.repository.OrderRepository orderRepository,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.orderManagementService = orderManagementService;
        this.orderRepository = orderRepository;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    /**
     * POST /api/orders
     * Creates a new order from customer checkout form (multi-item cart)
     * Supports idempotency via Idempotency-Key header to prevent double-charging
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrder(
            @Valid @RequestBody OrderRequestDTO request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        // Validate and normalize MeasurementUnit values before idempotency check
        normalizeOrderItemUnits(request);

        log.info("Received order request from customer: {}, items: {}",
                request.getCustomerName(),
                request.getItems() != null ? request.getItems().size() : 0);

        // If no idempotency key provided, generate one for backward compatibility
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            idempotencyKey = UUID.randomUUID().toString();
        }

        // Check idempotency
        Optional<IdempotencyKey> existingKey = idempotencyService.checkAndRecord(idempotencyKey, request);

        if (existingKey.isPresent()) {
            // Return cached response
            try {
                Map<String, Object> cachedResponse = objectMapper.readValue(
                        existingKey.get().getResponseData(),
                        new TypeReference<Map<String, Object>>() {
                        });
                log.info("Returning cached response for idempotency key: {}", idempotencyKey);
                return ResponseEntity.status(HttpStatus.OK).body(cachedResponse);
            } catch (Exception e) {
                log.error("Failed to deserialize cached response for idempotency key: {}", idempotencyKey, e);
                // If deserialization fails, proceed with normal flow
            }
        }

        try {
            // Detect POS vs Web order based on createdBy field
            // POS orders use 'pos_operator' or 'admin', web orders use 'WEBSITE'
            boolean isWebOrder = "WEBSITE".equalsIgnoreCase(request.getCreatedBy());

            // POST /api/orders is used by both web customer form and POS
            // Web orders: isWebOrder=true (PENDING state + notification)
            // POS orders: isWebOrder=false (ACCEPTED state + no notification)
            Order savedOrder = orderManagementService.createPosOrder(request, isWebOrder);

            Map<String, Object> response = new HashMap<>();
            response.put("id", savedOrder.getId());
            response.put("grandTotal", savedOrder.getGrandTotal());
            response.put("status", "success");
            response.put("message", "Order created successfully");

            // Store response for idempotency
            idempotencyService.storeResponse(idempotencyKey, response);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("Validation error creating order: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error creating order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", "An unexpected error occurred. Please try again."));
        }
    }

    private void normalizeOrderItemUnits(OrderRequestDTO request) {
        if (request.getItems() != null) {
            for (com.ghasl_service.demo.dto.OrderItemDTO item : request.getItems()) {
                if (item.getUnitName() != null) {
                    try {
                        // Validate enum conversion
                        com.ghasl_service.demo.model.MeasurementUnit.valueOf(item.getUnitName().toUpperCase());
                        item.setUnitName(item.getUnitName().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        // Fallback to default if invalid
                        item.setUnitName(com.ghasl_service.demo.model.MeasurementUnit.PER_PIECE.name());
                    }
                }
            }
        }
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<?> acceptOrder(@PathVariable Long id) {
        try {
            Order order = orderService.acceptOrder(id);
            return ResponseEntity.ok(order);
        } catch (OptimisticLockConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectOrder(@PathVariable Long id,
            @RequestBody(required = false) RejectionRequest request) {
        try {
            com.ghasl_service.demo.model.RejectionReason rejectionReason = (request != null
                    && request.getRejectionReason() != null)
                            ? request.getRejectionReason()
                            : com.ghasl_service.demo.model.RejectionReason.OTHER;
            Order order = orderService.rejectOrderWithReason(id, rejectionReason);
            return ResponseEntity.ok(order);
        } catch (OptimisticLockConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/rejection-reasons")
    public ResponseEntity<List<RejectionReasonResponse>> getRejectionReasons() {
        List<RejectionReasonResponse> reasons = Arrays.stream(RejectionReason.values())
                .map(RejectionReasonResponse::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(reasons);
    }

    @GetMapping("/pos-orders")
    public ResponseEntity<org.springframework.data.domain.Page<Order>> getPosOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        org.springframework.data.domain.Page<Order> ordersPage = orderRepository.findPosOrders(pageable);
        
        // Ensure OrderItems are loaded with their locked prices
        // This prevents lazy loading issues and ensures correct price display
        ordersPage.getContent().forEach(order -> {
            if (order.getItems() != null) {
                order.getItems().size(); // Force initialization
            }
        });
        
        return ResponseEntity.ok(ordersPage);
    }

    @PostMapping("/{id}/price")
    public ResponseEntity<?> updatePrice(@PathVariable Long id, @RequestParam BigDecimal price) {
        try {
            Order order = orderService.updateOrderPrice(id, price);
            return ResponseEntity.ok(order);
        } catch (OptimisticLockConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/revert")
    public ResponseEntity<?> revertOrder(@PathVariable Long id) {
        try {
            Order order = orderService.revertOrder(id);
            return ResponseEntity.ok(order);
        } catch (OptimisticLockConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
