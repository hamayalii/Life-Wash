package com.ghasl_service.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO containing aggregated report data.
 * All financial calculations are performed in the backend (Zero Trust Architecture).
 */
public class ReportGenerationResponse {
    
    private ReportGenerationRequest.ReportType reportType;
    private String periodLabel;
    private LocalDateTime generatedAt;
    private String reportText; // Formatted text for display
    
    // Financial aggregations
    private BigDecimal totalRevenue;
    private BigDecimal totalProfit;
    private int totalOrders;
    private int acceptedOrders;
    private int rejectedOrders;
    private int pendingOrders;
    
    // Customer metrics
    private int totalCustomers;
    private BigDecimal averageOrderValue;
    
    // Top services breakdown
    private List<ServiceBreakdown> topServices;
    
    // Channel breakdown
    private List<ChannelBreakdown> channels;
    
    public static class ServiceBreakdown {
        private String serviceNameKurdish;
        private String serviceNameEnglish;
        private BigDecimal revenue;
        private int orderCount;
        private BigDecimal profit;
        
        public ServiceBreakdown() {}
        
        public ServiceBreakdown(String serviceNameKurdish, String serviceNameEnglish, 
                               BigDecimal revenue, int orderCount, BigDecimal profit) {
            this.serviceNameKurdish = serviceNameKurdish;
            this.serviceNameEnglish = serviceNameEnglish;
            this.revenue = revenue;
            this.orderCount = orderCount;
            this.profit = profit;
        }
        
        // Getters and setters
        public String getServiceNameKurdish() { return serviceNameKurdish; }
        public void setServiceNameKurdish(String serviceNameKurdish) { this.serviceNameKurdish = serviceNameKurdish; }
        
        public String getServiceNameEnglish() { return serviceNameEnglish; }
        public void setServiceNameEnglish(String serviceNameEnglish) { this.serviceNameEnglish = serviceNameEnglish; }
        
        public BigDecimal getRevenue() { return revenue; }
        public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
        
        public int getOrderCount() { return orderCount; }
        public void setOrderCount(int orderCount) { this.orderCount = orderCount; }
        
        public BigDecimal getProfit() { return profit; }
        public void setProfit(BigDecimal profit) { this.profit = profit; }
    }
    
    public static class ChannelBreakdown {
        private String channelName;
        private BigDecimal revenue;
        private int orderCount;
        
        public ChannelBreakdown() {}
        
        public ChannelBreakdown(String channelName, BigDecimal revenue, int orderCount) {
            this.channelName = channelName;
            this.revenue = revenue;
            this.orderCount = orderCount;
        }
        
        // Getters and setters
        public String getChannelName() { return channelName; }
        public void setChannelName(String channelName) { this.channelName = channelName; }
        
        public BigDecimal getRevenue() { return revenue; }
        public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
        
        public int getOrderCount() { return orderCount; }
        public void setOrderCount(int orderCount) { this.orderCount = orderCount; }
    }
    
    // Constructors
    public ReportGenerationResponse() {}
    
    // Getters and setters
    public ReportGenerationRequest.ReportType getReportType() { return reportType; }
    public void setReportType(ReportGenerationRequest.ReportType reportType) { this.reportType = reportType; }
    
    public String getPeriodLabel() { return periodLabel; }
    public void setPeriodLabel(String periodLabel) { this.periodLabel = periodLabel; }
    
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    
    public String getReportText() { return reportText; }
    public void setReportText(String reportText) { this.reportText = reportText; }
    
    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
    
    public BigDecimal getTotalProfit() { return totalProfit; }
    public void setTotalProfit(BigDecimal totalProfit) { this.totalProfit = totalProfit; }
    
    public int getTotalOrders() { return totalOrders; }
    public void setTotalOrders(int totalOrders) { this.totalOrders = totalOrders; }
    
    public int getAcceptedOrders() { return acceptedOrders; }
    public void setAcceptedOrders(int acceptedOrders) { this.acceptedOrders = acceptedOrders; }
    
    public int getRejectedOrders() { return rejectedOrders; }
    public void setRejectedOrders(int rejectedOrders) { this.rejectedOrders = rejectedOrders; }
    
    public int getPendingOrders() { return pendingOrders; }
    public void setPendingOrders(int pendingOrders) { this.pendingOrders = pendingOrders; }
    
    public int getTotalCustomers() { return totalCustomers; }
    public void setTotalCustomers(int totalCustomers) { this.totalCustomers = totalCustomers; }
    
    public BigDecimal getAverageOrderValue() { return averageOrderValue; }
    public void setAverageOrderValue(BigDecimal averageOrderValue) { this.averageOrderValue = averageOrderValue; }
    
    public List<ServiceBreakdown> getTopServices() { return topServices; }
    public void setTopServices(List<ServiceBreakdown> topServices) { this.topServices = topServices; }
    
    public List<ChannelBreakdown> getChannels() { return channels; }
    public void setChannels(List<ChannelBreakdown> channels) { this.channels = channels; }
}
