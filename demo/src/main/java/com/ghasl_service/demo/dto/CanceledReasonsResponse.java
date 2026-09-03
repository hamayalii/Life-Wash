package com.ghasl_service.demo.dto;

/**
 * DTO for canceled orders analysis chart
 */
public class CanceledReasonsResponse {
    private String reason;
    private String kurdishLabel;
    private Long count;

    public CanceledReasonsResponse(String reason, String kurdishLabel, Long count) {
        this.reason = reason;
        this.kurdishLabel = kurdishLabel;
        this.count = count;
    }

    // Getters
    public String getReason() { return reason; }
    public String getKurdishLabel() { return kurdishLabel; }
    public Long getCount() { return count; }
}
