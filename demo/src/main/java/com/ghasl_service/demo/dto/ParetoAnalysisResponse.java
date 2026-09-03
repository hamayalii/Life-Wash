package com.ghasl_service.demo.dto;

import java.util.List;

public class ParetoAnalysisResponse {
    private List<ParetoAnalysisDTO> services;
    private String currency;
    private String period;

    public ParetoAnalysisResponse() {
    }

    public ParetoAnalysisResponse(List<ParetoAnalysisDTO> services, String currency, String period) {
        this.services = services;
        this.currency = currency;
        this.period = period;
    }

    // Getters and Setters

    public List<ParetoAnalysisDTO> getServices() {
        return services;
    }

    public void setServices(List<ParetoAnalysisDTO> services) {
        this.services = services;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }
}
