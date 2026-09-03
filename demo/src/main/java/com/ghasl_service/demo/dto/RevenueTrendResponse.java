package com.ghasl_service.demo.dto;

import java.math.BigDecimal;

/**
 * Response DTO for GET /api/v1/admin/dashboard/revenue-trend
 */
public record RevenueTrendResponse(
    String label,
    BigDecimal amountIQD
) {}
