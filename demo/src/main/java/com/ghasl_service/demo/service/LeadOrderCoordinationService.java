package com.ghasl_service.demo.service;

import com.ghasl_service.demo.dto.RugWashLeadRequest;
import com.ghasl_service.demo.event.OrderSubmittedEvent;
import com.ghasl_service.demo.model.Order;
import com.ghasl_service.demo.model.RugWashLead;
import com.ghasl_service.demo.repository.LeadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the Lead → Order flow in a single transaction boundary.
 *
 * <h3>Transactional design decision</h3>
 * <ul>
 *   <li>Lead persistence and order creation are wrapped in one
 *       {@code @Transactional} method here, NOT in the controller.</li>
 *   <li>{@link OrderService#createOrder(Order)} participates in the outer
 *       transaction (propagation=REQUIRED), so a crash in order-creation
 *       rolls back the lead row too — no orphan state.</li>
 *   <li>If {@link PricingService#resolve} throws {@link IllegalArgumentException}
 *       (unknown rugType or invalid quantity), the transaction rolls back and
 *       the lead is NOT saved — the client sent bad data and gets a 400.</li>
 * </ul>
 *
 * <h3>Three-outcome flow</h3>
 * <pre>
 *   Computed(amount)     → Order(price=amount)   → orderService.createOrder → Telegram fires
 *   PendingAdmin(reason) → Order(price=null)     → orderService.createOrder → Telegram fires
 *   NotApplicable()      → no Order              → lead only, manual follow-up
 * </pre>
 */
@Service
public class LeadOrderCoordinationService {

    private static final Logger log = LoggerFactory.getLogger(LeadOrderCoordinationService.class);

    private final LeadRepository leadRepository;
    private final OrderService orderService;
    private final PricingService pricingService;

    public LeadOrderCoordinationService(LeadRepository leadRepository,
                                        OrderService orderService,
                                        PricingService pricingService) {
        this.leadRepository = leadRepository;
        this.orderService   = orderService;
        this.pricingService = pricingService;
    }

    /**
     * Processes an incoming lead-capture request.
     *
     * @param request the validated request from the controller
     * @return {@link LeadCaptureResult} describing what was created
     * @throws IllegalArgumentException if rugType is unknown or quantity is invalid
     *         (caller should map this to HTTP 400)
     */
    public LeadCaptureResult processLead(RugWashLeadRequest request) {

        // ── 1. Persist lead (always, for all valid and invalid rugTypes) ──────────
        RugWashLead lead = new RugWashLead(
                request.customerName(),
                request.phoneNumber(),
                request.address(),
                request.rugType(),
                request.message()
        );
        RugWashLead savedLead = leadRepository.save(lead);
        log.info("Lead saved [id={}] for customer='{}', rugType='{}', quantity={}",
                savedLead.getId(), savedLead.getCustomerName(),
                savedLead.getRugType(), request.quantity());

        // ── 2. Resolve price — if invalid, return invalid_quantity (lead is kept) 
        PriceResolution resolution;
        try {
            resolution = pricingService.resolve(request.rugType(), request.quantity());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid quantity/type for lead [id={}]: {}", savedLead.getId(), e.getMessage());
            return LeadCaptureResult.invalidQuantity(savedLead.getId(), e.getMessage());
        }

        // ── 3. Branch on PriceResolution ─────────────────────────────────────
        return switch (resolution) {

            case PriceResolution.Computed(var amount) -> {
                Order order = new Order(
                        request.customerName(),
                        request.phoneNumber(),
                        request.rugType(),
                        amount,
                        request.address(),
                        request.message(),
                        request.quantity() != null ? java.math.BigDecimal.valueOf(request.quantity()) : null
                );
                
                // ZERO-TRUST: Forcefully overwrite prices with backend-calculated values
                // Do not trust ANY financial value from frontend
                order.setPrice(amount);
                order.setGrandTotal(amount);
                
                // ERROR level trace logging (as requested by user)
                log.error("TRACE - OrderSave: Saving Order for Customer='{}'. Forced Price={}, Forced GrandTotal={}",
                    request.customerName(), order.getPrice(), order.getGrandTotal());
                
                Order saved = orderService.createOrder(order);
                log.info("Order [id={}] created from lead [id={}]: rugType='{}', price={} IQD, grandTotal={} IQD",
                        saved.getId(), savedLead.getId(), saved.getRugType(), saved.getPrice(), saved.getGrandTotal());
                yield LeadCaptureResult.success(savedLead.getId(), saved.getId(), "computed");
            }

            case PriceResolution.PendingAdmin(var reason) -> {
                // Create order with price=null — admin confirms by phone.
                // OrderSubmittedEvent still fires so Telegram notifies the admin.
                Order order = new Order(
                        request.customerName(),
                        request.phoneNumber(),
                        request.rugType(),
                        null,                   // price TBD
                        request.address(),
                        request.message(),
                        request.quantity() != null ? java.math.BigDecimal.valueOf(request.quantity()) : null
                );
                
                // ZERO-TRUST: Ensure grandTotal is null for pending admin orders
                order.setPrice(null);
                order.setGrandTotal(null);
                
                Order saved = orderService.createOrder(order);
                log.info("Order [id={}] (PENDING price) created from lead [id={}]: " +
                         "rugType='{}', quantity={}, reason='{}'",
                         saved.getId(), savedLead.getId(),
                         saved.getRugType(), saved.getQuantity(), reason);
                yield LeadCaptureResult.success(savedLead.getId(), saved.getId(), "pending_admin");
            }

            case PriceResolution.NotApplicable() -> {
                log.warn("rugType='{}' → NotApplicable. Lead [id={}] saved; " +
                         "no order created — manual quote required.",
                         request.rugType(), savedLead.getId());
                yield LeadCaptureResult.notApplicable(savedLead.getId());
            }
        };
    }

    // ── Inner result DTO ───────────────────────────────────────────────────────

    /**
     * Value object returned to the controller. {@code pricingStatus} mirrors the
     * resolved {@link PriceResolution} variant: "computed" | "pending_admin" |
     * "not_applicable".
     */
    public static final class LeadCaptureResult {

        public enum Outcome { ORDER_CREATED, NO_ORDER }

        private final Outcome outcome;
        private final String  pricingStatus;  // "computed" | "pending_admin" | "not_applicable"
        private final Long    leadId;
        private final Long    orderId;        // null when outcome == NO_ORDER

        private LeadCaptureResult(Outcome outcome, String pricingStatus, Long leadId, Long orderId) {
            this.outcome       = outcome;
            this.pricingStatus = pricingStatus;
            this.leadId        = leadId;
            this.orderId       = orderId;
        }

        static LeadCaptureResult success(Long leadId, Long orderId, String pricingStatus) {
            return new LeadCaptureResult(Outcome.ORDER_CREATED, pricingStatus, leadId, orderId);
        }

        static LeadCaptureResult notApplicable(Long leadId) {
            return new LeadCaptureResult(Outcome.NO_ORDER, "not_applicable", leadId, null);
        }

        static LeadCaptureResult invalidQuantity(Long leadId, String errorMessage) {
            return new LeadCaptureResult(Outcome.NO_ORDER, "invalid_quantity", leadId, null);
        }

        public Outcome getOutcome()         { return outcome; }
        public String  getPricingStatus()   { return pricingStatus; }
        public Long    getLeadId()          { return leadId; }
        public Long    getOrderId()         { return orderId; }
        public boolean isOrderCreated()     { return outcome == Outcome.ORDER_CREATED; }
    }
}
