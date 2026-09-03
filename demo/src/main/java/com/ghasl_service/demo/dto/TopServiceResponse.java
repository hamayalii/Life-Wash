package com.ghasl_service.demo.dto;

/**
 * Response DTO for GET /api/v1/admin/dashboard/top-services
 */
public record TopServiceResponse(
    String rugType,
    String label,
    long count
) {}
