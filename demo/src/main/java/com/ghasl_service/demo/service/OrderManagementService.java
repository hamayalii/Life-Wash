package com.ghasl_service.demo.service;

import com.ghasl_service.demo.dto.OrderItemDTO;
import com.ghasl_service.demo.dto.OrderRequestDTO;
import com.ghasl_service.demo.model.MeasurementUnit;
import com.ghasl_service.demo.model.Order;
import com.ghasl_service.demo.model.OrderItem;
import com.ghasl_service.demo.model.OrderSource;
import com.ghasl_service.demo.model.OutboxEvent;
import com.ghasl_service.demo.model.Service;
import com.ghasl_service.demo.model.ServiceCategory;
import com.ghasl_service.demo.model.ServicePricing;
import com.ghasl_service.demo.repository.OrderRepository;
import com.ghasl_service.demo.repository.OutboxEventRepository;
import com.ghasl_service.demo.repository.ServiceCategoryRepository;
import com.ghasl_service.demo.repository.ServicePricingRepository;
import com.ghasl_service.demo.repository.ServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class OrderManagementService {

    private static final Logger log = LoggerFactory.getLogger(OrderManagementService.class);

    // Maximum allowed unit price to prevent price manipulation attacks
    // 1,000,000 IQD per item (adjustable based on business requirements)
    private static final BigDecimal MAX_UNIT_PRICE = new BigDecimal("1000000.00");

    private final OrderRepository orderRepository;
    private final ServiceRepository serviceRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final ServicePricingRepository servicePricingRepository;
    private final OutboxEventRepository outboxEventRepository;

    public OrderManagementService(OrderRepository orderRepository, ServiceRepository serviceRepository, 
                                   ServiceCategoryRepository serviceCategoryRepository,
                                   ServicePricingRepository servicePricingRepository,
                                   OutboxEventRepository outboxEventRepository) {
        this.orderRepository = orderRepository;
        this.serviceRepository = serviceRepository;
        this.serviceCategoryRepository = serviceCategoryRepository;
        this.servicePricingRepository = servicePricingRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    /**
     * Creates a POS or web order with strict data integrity validation.
     * Backend recalculate all totals from verified database prices.
     * Never trust frontend calculations for financial data.
     *
     * @param request The order request DTO from the frontend
     * @param isWebOrder true for web customer orders (PENDING state + notification), false for POS (ACCEPTED + no notification)
     * @return The saved Order entity
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional
    public Order createPosOrder(OrderRequestDTO request, boolean isWebOrder) {
        log.info("Creating POS order for customer: {}, operator: {}",
                request.getCustomerName(), request.getCreatedBy());

        // Validate required fields
        if (request.getCreatedBy() == null || request.getCreatedBy().isBlank()) {
            throw new IllegalArgumentException("createdBy is required for auditing. Anonymous orders are prohibited.");
        }

        if (request.getCustomerName() == null || request.getCustomerName().isBlank()) {
            throw new IllegalArgumentException("customerName is required");
        }

        if (request.getPhoneNumber() == null || request.getPhoneNumber().isBlank()) {
            throw new IllegalArgumentException("phoneNumber is required");
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        // CRITICAL FIX: Check idempotency key to prevent duplicate orders
        // Rapid double-clicks or network retries can create duplicate orders without this check
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required for duplicate prevention");
        }

        // Check if an order with this idempotency key already exists
        Order existingOrder = orderRepository.findByIdempotencyKey(request.getIdempotencyKey()).orElse(null);
        if (existingOrder != null) {
            log.warn("Duplicate order attempt detected with idempotencyKey: {}. Returning existing order ID: {}",
                    request.getIdempotencyKey(), existingOrder.getId());
            return existingOrder;
        }

        // Create the order entity
        Order order = new Order();
        order.setCustomerName(request.getCustomerName());
        order.setPhoneNumber(request.getPhoneNumber());
        order.setAddress(request.getAddress());
        order.setMessage(request.getNotes());
        order.setCreatedBy(request.getCreatedBy());
        order.setIdempotencyKey(request.getIdempotencyKey());
        
        // Zero Trust: Backend enforces state based on order source
        if (isWebOrder) {
            // Web orders require admin review - force PENDING state
            order.setWorkStatus(Order.WorkStatus.PENDING);
            order.setOrderSource(OrderSource.WEB);
        } else {
            // POS orders are direct in-person transactions - auto-accept them
            order.setWorkStatus(Order.WorkStatus.ACCEPTED);
            order.setOrderSource(OrderSource.POS);
        }

        BigDecimal grandTotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        // Batch fetch all services to avoid N+1 queries
        List<Long> serviceIds = request.getItems().stream()
                .map(OrderItemDTO::getServiceId)
                .distinct()
                .toList();

        List<Service> services = serviceRepository.findAllById(serviceIds);
        Map<Long, Service> serviceMap = services.stream()
                .collect(Collectors.toMap(Service::getId, Function.identity()));

        // Process each order item using pre-fetched services
        for (OrderItemDTO itemDTO : request.getItems()) {
            log.info("Processing item: serviceId={}, quantity={}", itemDTO.getServiceId(), itemDTO.getQuantity());

            // Fetch the service from pre-fetched map
            Service service = serviceMap.get(itemDTO.getServiceId());

            if (service == null) {
                // Fallback: Try to find Service by category ID (handles legacy/new service ID mismatch)
                service = serviceRepository.findById(itemDTO.getServiceId())
                        .orElse(null);
                
                if (service == null) {
                    // Second fallback: Try to find Service by category ID
                    service = serviceRepository.findByCategoryId(itemDTO.getServiceId())
                            .orElse(null);
                }
                
                if (service == null) {
                    // Third fallback: Try to find ServiceCategory and create a temporary service reference
                    ServiceCategory category = serviceCategoryRepository.findById(itemDTO.getServiceId())
                            .orElse(null);
                    
                    if (category != null) {
                        // Use category for pricing lookup even if service is null
                        log.warn("Service not found for ID {}, but category found: {}. Using category for pricing.",
                                itemDTO.getServiceId(), category.getEnglishName());
                        
                        // Create a temporary service object for pricing calculation
                        service = new Service();
                        service.setCategory(category);
                        service.setName(category.getEnglishName());
                        service.setDefaultUnitType(MeasurementUnit.PER_PIECE);
                    } else {
                        throw new IllegalArgumentException(
                                "Service and Category not found with id: " + itemDTO.getServiceId());
                    }
                }
            }

            // Validate quantity
            if (itemDTO.getQuantity() == null || itemDTO.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "Quantity must be > 0 for service: " + service.getName());
            }

            // Determine unit name using MeasurementUnit enum
            MeasurementUnit unitName;
            String unitNameString = itemDTO.getUnitName();
            
            if (unitNameString != null && !unitNameString.isBlank()) {
                // Convert from DTO string to enum
                try {
                    unitName = MeasurementUnit.valueOf(unitNameString.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid unit name from DTO: {}, using service default", unitNameString);
                    unitName = service.getDefaultUnitType();
                }
            } else {
                // Use service default
                unitName = service.getDefaultUnitType();
            }
            
            // Fallback if still null
            if (unitName == null) {
                unitName = MeasurementUnit.PER_PIECE; // Safe default
            }

            // CRITICAL SECURITY LOGIC: Determine unit price using P(q) = (B / S) * q
            // Single Source of Truth: ServicePricing entity
            // Zero Trust: Backend strictly enforces discount calculation
            BigDecimal unitPrice;
            ServiceCategory category = service.getCategory();
            ServicePricing pricing = (category != null) 
                ? servicePricingRepository.findByServiceCategory(category).orElse(null) 
                : null;
            
            if (pricing != null && pricing.getBasePrice() != null) {
                // Service has fixed price from ServicePricing - override frontend price
                BigDecimal basePrice = pricing.getBasePrice();
                
                // STRICT DISCOUNT CALCULATION (Zero Trust)
                // Check if discount is active and within validity period
                boolean isDiscountActive = pricing.getIsDiscountActive() != null && pricing.getIsDiscountActive();
                boolean isWithinDiscountPeriod = false;
                
                // Allow discount if dates are not set (indefinite) OR if current time is within range
                if (isDiscountActive) {
                    if (pricing.getDiscountStartDate() == null && pricing.getDiscountEndDate() == null) {
                        // No date constraints - discount is active indefinitely
                        isWithinDiscountPeriod = true;
                    } else if (pricing.getDiscountStartDate() != null && pricing.getDiscountEndDate() != null) {
                        // Both dates set - check if current time is within range
                        java.time.LocalDateTime now = java.time.LocalDateTime.now();
                        isWithinDiscountPeriod = !now.isBefore(pricing.getDiscountStartDate()) && 
                                                 !now.isAfter(pricing.getDiscountEndDate());
                    } else if (pricing.getDiscountStartDate() != null) {
                        // Only start date set - check if we're past it
                        java.time.LocalDateTime now = java.time.LocalDateTime.now();
                        isWithinDiscountPeriod = !now.isBefore(pricing.getDiscountStartDate());
                    } else if (pricing.getDiscountEndDate() != null) {
                        // Only end date set - check if we're before it
                        java.time.LocalDateTime now = java.time.LocalDateTime.now();
                        isWithinDiscountPeriod = !now.isAfter(pricing.getDiscountEndDate());
                    }
                }
                
                // Apply discount if active and within period
                BigDecimal effectivePrice = basePrice;
                if (isDiscountActive && isWithinDiscountPeriod) {
                    if (pricing.getDiscountPrice() != null && pricing.getDiscountPrice().compareTo(BigDecimal.ZERO) > 0) {
                        // Use explicit discount price
                        effectivePrice = pricing.getDiscountPrice();
                        log.error("DISCOUNT APPLIED for '{}': basePrice={} -> discountPrice={} (valid: {} to {})",
                                service.getName(), basePrice, effectivePrice, 
                                pricing.getDiscountStartDate(), pricing.getDiscountEndDate());
                    } else if (pricing.getDiscountPercentage() != null && pricing.getDiscountPercentage().compareTo(BigDecimal.ZERO) > 0) {
                        // Calculate discount from percentage
                        BigDecimal discountAmount = basePrice.multiply(pricing.getDiscountPercentage())
                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                        effectivePrice = basePrice.subtract(discountAmount);
                        log.error("DISCOUNT APPLIED for '{}': basePrice={} - {}% = {} (valid: {} to {})",
                                service.getName(), basePrice, pricing.getDiscountPercentage(), effectivePrice,
                                pricing.getDiscountStartDate(), pricing.getDiscountEndDate());
                    }
                } else {
                    log.error("NO DISCOUNT for '{}': isDiscountActive={}, isWithinPeriod={}, basePrice={}",
                        service.getName(), isDiscountActive, isWithinDiscountPeriod, basePrice);
                }
                
                // SPECIAL CASE: Sofa per-person pricing calculation
                // P(q) = (B / S) * q where B = effectivePrice, S = sofaStandardSetSize
                if (unitName == MeasurementUnit.PER_PERSON) {
                    // Fail-safe: fallback to 10 if sofaStandardSetSize is null or <= 0
                    int setSize = (pricing.getSofaStandardSetSize() != null && pricing.getSofaStandardSetSize() > 0) 
                                  ? pricing.getSofaStandardSetSize() 
                                  : 10;
                    BigDecimal standardSetSize = new BigDecimal(setSize);
                    unitPrice = effectivePrice.divide(standardSetSize, 2, RoundingMode.HALF_UP);
                    log.info("Sofa service '{}' per-person pricing: effectivePrice={} / standardSetSize={} = {} IQD/person",
                            service.getName(), effectivePrice, setSize, unitPrice);
                } else {
                    // Default calculation for PER_PIECE, PER_METER, etc.
                    unitPrice = effectivePrice;
                    log.info("Service '{}' has effective price: {} IQD/unit (discount applied: {})",
                            service.getName(), unitPrice, (isDiscountActive && isWithinDiscountPeriod));
                }
            } else if (service.getBasePrice() != null) {
                // Fallback: Use Service entity basePrice if ServicePricing is unavailable
                unitPrice = service.getBasePrice();
                log.warn("ServicePricing not found for '{}', using Service entity basePrice as fallback.", service.getName());
            } else {
                // Service requires negotiated price - validate and use frontend price
                if (itemDTO.getUnitPrice() == null || itemDTO.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException(
                            "Service '" + service.getName() + "' requires negotiated price. " +
                                    "Frontend must provide a valid unitPrice > 0.");
                }
                
                // CRITICAL SECURITY VALIDATION: Enforce upper bound on negotiated prices
                // Prevents price manipulation attacks where attackers submit unreasonably high prices
                if (itemDTO.getUnitPrice().compareTo(MAX_UNIT_PRICE) > 0) {
                    throw new IllegalArgumentException(
                            "Service '" + service.getName() + "' negotiated price exceeds maximum allowed limit. " +
                                    "Maximum allowed unit price: " + MAX_UNIT_PRICE + " IQD. Provided: " + itemDTO.getUnitPrice() + " IQD.");
                }
                
                unitPrice = itemDTO.getUnitPrice();
                log.info("Service '{}' has no fixed price. Using negotiated price: {} IQD/unit from frontend.",
                        service.getName(), unitPrice);
            }

            // Calculate item total (quantity * unitPrice)
            BigDecimal itemTotal = unitPrice.multiply(itemDTO.getQuantity())
                    .setScale(2, RoundingMode.HALF_UP);

            // Create OrderItem entity
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            
            // Only set service if it's a real persisted entity (not temporary)
            if (service.getId() != null) {
                orderItem.setService(service);
            }

            // Defensive category population
            if (service.getCategory() != null) {
                orderItem.setServiceCategory(service.getCategory());
            } else {
                // Variable renamed to fallbackCategory. Fetch by itemDTO.getServiceId() to avoid NPE.
                ServiceCategory fallbackCategory = serviceCategoryRepository.findById(itemDTO.getServiceId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found for service ID: " + itemDTO.getServiceId()));
                orderItem.setServiceCategory(fallbackCategory);
            }

            orderItem.setQuantity(itemDTO.getQuantity());
            orderItem.setUnitName(unitName);
            orderItem.setUnitPrice(unitPrice);
            orderItem.setTotalPrice(itemTotal);

            orderItems.add(orderItem);
            grandTotal = grandTotal.add(itemTotal);

            log.info("Added item: service={}, qty={}, unitPrice={}, total={}",
                    service.getName(), itemDTO.getQuantity(), unitPrice, itemTotal);
        }

        // ERROR level trace logging (as requested by user)
        log.error("TRACE - OrderManagementService: WebOrder={}, Customer='{}, ServerCalculatedGrandTotal={}, ItemsCount={}",
            isWebOrder, request.getCustomerName(), grandTotal, orderItems.size());

        // Set grand total and items
        order.setGrandTotal(grandTotal.setScale(2, RoundingMode.HALF_UP));
        order.setItems(orderItems);

        // Save order (cascade will save order items)
        Order savedOrder = orderRepository.save(order);

        log.info("Order created successfully: orderId={}, grandTotal={} IQD, items={}, isWebOrder={}",
                savedOrder.getId(), savedOrder.getGrandTotal(), orderItems.size(), isWebOrder);

        // CRITICAL FIX: Save outbox event in the SAME transaction
        // This ensures event is persisted even if server crashes
        // The OutboxEventProcessor will handle updating customer value
        OutboxEvent outboxEvent = new OutboxEvent("OrderSubmittedEvent", String.valueOf(savedOrder.getId()));
        outboxEventRepository.save(outboxEvent);
        
        log.info("Order created and outbox event saved: orderId={}, outboxEventId={}", 
                savedOrder.getId(), outboxEvent.getId());

        return savedOrder;
    }
}
