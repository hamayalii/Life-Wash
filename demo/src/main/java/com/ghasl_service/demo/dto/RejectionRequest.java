package com.ghasl_service.demo.dto;

import com.ghasl_service.demo.model.RejectionReason;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for rejecting an order with a reason
 */
public class RejectionRequest {
    @NotNull(message = "rejectionReason is required")
    private RejectionReason rejectionReason;

    public RejectionRequest() {}

    public RejectionRequest(RejectionReason rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    // Getters and Setters
    public RejectionReason getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(RejectionReason rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}
