package com.ghasl_service.demo.controller;

import com.ghasl_service.demo.dto.RugWashLeadRequest;
import com.ghasl_service.demo.service.LeadOrderCoordinationService;
import com.ghasl_service.demo.service.LeadOrderCoordinationService.LeadCaptureResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller for the lead-capture endpoint.
 *
 * <p>
 * Intentionally thin: delegates all business logic and transaction management
 * to {@link LeadOrderCoordinationService}.
 *
 * <h3>Response body {@code status} values</h3>
 * <ul>
 * <li>{@code "success"} + {@code pricingStatus="computed"} — order created with
 * computed price</li>
 * <li>{@code "success"} + {@code pricingStatus="pending_admin"} — order
 * created, price TBD by admin</li>
 * <li>{@code "lead_captured_pending_quote"} — lead saved only (antique /
 * NotApplicable)</li>
 * </ul>
 *
 * <h3>Error responses</h3>
 * <ul>
 * <li>HTTP 400 — unknown rugType or invalid quantity (client error)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/leads")
public class LeadCaptureController {

    private static final Logger log = LoggerFactory.getLogger(LeadCaptureController.class);

    private final LeadOrderCoordinationService coordinationService;

    public LeadCaptureController(LeadOrderCoordinationService coordinationService) {
        this.coordinationService = coordinationService;
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<Map<String, Object>> captureLead(
            @RequestBody RugWashLeadRequest request) {

        log.info("Received lead capture: customer='{}', rugType='{}', quantity={}",
                request.customerName(), request.rugType(), request.quantity());

        LeadCaptureResult result = coordinationService.processLead(request);

        Map<String, Object> body = new LinkedHashMap<>();

        if (result.isOrderCreated()) {
            body.put("status", "success");
            body.put("pricingStatus", result.getPricingStatus());
            body.put("message", "pending_admin".equals(result.getPricingStatus())
                    ? "Your request has been received. Our team will call you to confirm the final price."
                    : "Lead and order created successfully.");
            body.put("leadId", result.getLeadId());
            body.put("orderId", result.getOrderId());
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } else if ("invalid_quantity".equals(result.getPricingStatus())) {
            body.put("status", "error");
            body.put("pricingStatus", result.getPricingStatus());
            body.put("message", "Invalid quantity or rug type provided.");
            body.put("leadId", result.getLeadId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        } else {
            body.put("status", "lead_captured_pending_quote");
            body.put("pricingStatus", result.getPricingStatus());
            body.put("message",
                    "Your request has been received. A team member will contact you " +
                            "to discuss pricing and confirm your order.");
            body.put("leadId", result.getLeadId());
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        }
    }
}
