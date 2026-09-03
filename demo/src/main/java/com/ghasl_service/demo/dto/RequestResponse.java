package com.ghasl_service.demo.dto;

import java.math.BigDecimal;

/**
 * Response DTO for GET /api/v1/admin/dashboard/requests
 */
public record RequestResponse(
    Long orderId,
    String customerName,
    String requestedAt,
    String serviceNamesFormatted,
    BigDecimal quantity,
    String quantityLabel,
    BigDecimal price,
    String workStatus,
    String address,
    String message
) {}
