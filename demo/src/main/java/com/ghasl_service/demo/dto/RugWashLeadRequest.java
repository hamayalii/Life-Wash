package com.ghasl_service.demo.dto;

/**
 * Immutable record carrying the data POSTed by the lead-capture form.
 *
 * <p>{@code quantity} is nullable:
 * <ul>
 *   <li>For persian/shag: metres (decimal allowed, e.g. 3.5)</li>
 *   <li>For silk/synthetic: integer piece count</li>
 *   <li>For wool: seat count (integer)</li>
 *   <li>For antique: not submitted (null) — no order is created</li>
 * </ul>
 */
public record RugWashLeadRequest(
        String customerName,
        String phoneNumber,
        String address,
        String rugType,
        String message,
        Double quantity
) {}
