package com.ghasl_service.demo.dto;

/**
 * Request DTO for generating reports.
 * Specifies the type of report to generate (Daily, Monthly, Yearly).
 */
public class ReportGenerationRequest {
    
    public enum ReportType {
        DAILY,
        MONTHLY,
        YEARLY
    }
    
    private ReportType reportType;
    
    public ReportGenerationRequest() {}
    
    public ReportGenerationRequest(ReportType reportType) {
        this.reportType = reportType;
    }
    
    public ReportType getReportType() {
        return reportType;
    }
    
    public void setReportType(ReportType reportType) {
        this.reportType = reportType;
    }
}
