package com.ghasl_service.demo.dto;

import com.ghasl_service.demo.model.RejectionReason;

/**
 * DTO for returning rejection reason options to frontend
 */
public class RejectionReasonResponse {
    private String value;
    private String kurdishLabel;

    public RejectionReasonResponse(RejectionReason reason) {
        this.value = reason.getValue();
        this.kurdishLabel = reason.getKurdishTranslation();
    }

    // Getters
    public String getValue() { return value; }
    public String getKurdishLabel() { return kurdishLabel; }
}
