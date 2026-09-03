package com.ghasl_service.demo.service;

import com.ghasl_service.demo.dto.ReportGenerationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

/**
 * Service for exporting reports to various formats (CSV, PDF placeholder).
 * Production-ready architecture with CSV implementation and PDF placeholder.
 */
@Service
public class ReportExportService {
    
    private static final Logger log = LoggerFactory.getLogger(ReportExportService.class);
    
    /**
     * Exports report data to CSV format.
     * This is a production-ready implementation using standard Java.
     */
    public byte[] exportToCsv(ReportGenerationResponse report) {
        log.info("Exporting report to CSV: {}", report != null ? report.getReportType() : "null report");
        
        if (report == null) {
            log.error("ReportGenerationResponse is null");
            throw new IllegalArgumentException("ReportGenerationResponse cannot be null");
        }
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try {
            // Write CSV header
            String header = "Report Type,Period,Generated At,Total Revenue,Total Profit,Total Orders,Accepted Orders,Rejected Orders,Pending Orders,Total Customers,Average Order Value\n";
            outputStream.write(header.getBytes(StandardCharsets.UTF_8));
            
            // Write summary row with null-safe checks
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String summary = String.format("%s,%s,%s,%s,%s,%d,%d,%d,%d,%d,%s\n",
                report.getReportType() != null ? report.getReportType() : "UNKNOWN",
                report.getPeriodLabel() != null ? report.getPeriodLabel() : "",
                report.getGeneratedAt() != null ? report.getGeneratedAt().format(formatter) : "",
                report.getTotalRevenue() != null ? report.getTotalRevenue().toString() : "0",
                report.getTotalProfit() != null ? report.getTotalProfit().toString() : "0",
                report.getTotalOrders(),
                report.getAcceptedOrders(),
                report.getRejectedOrders(),
                report.getPendingOrders(),
                report.getTotalCustomers(),
                report.getAverageOrderValue() != null ? report.getAverageOrderValue().toString() : "0"
            );
            outputStream.write(summary.getBytes(StandardCharsets.UTF_8));
            
            // Write service breakdown section with null-safe checks
            outputStream.write("\nService Breakdown\n".getBytes(StandardCharsets.UTF_8));
            outputStream.write("Service Name (Kurdish),Service Name (English),Revenue,Order Count,Profit\n".getBytes(StandardCharsets.UTF_8));
            
            if (report.getTopServices() != null) {
                for (ReportGenerationResponse.ServiceBreakdown service : report.getTopServices()) {
                    if (service == null) continue;
                    String serviceRow = String.format("%s,%s,%s,%d,%s\n",
                        escapeCsv(service.getServiceNameKurdish()),
                        escapeCsv(service.getServiceNameEnglish()),
                        service.getRevenue() != null ? service.getRevenue().toString() : "0",
                        service.getOrderCount(),
                        service.getProfit() != null ? service.getProfit().toString() : "0"
                    );
                    outputStream.write(serviceRow.getBytes(StandardCharsets.UTF_8));
                }
            }
            
            // Write channel breakdown section with null-safe checks
            outputStream.write("\nChannel Breakdown\n".getBytes(StandardCharsets.UTF_8));
            outputStream.write("Channel Name,Revenue,Order Count\n".getBytes(StandardCharsets.UTF_8));
            
            if (report.getChannels() != null) {
                for (ReportGenerationResponse.ChannelBreakdown channel : report.getChannels()) {
                    if (channel == null) continue;
                    String channelRow = String.format("%s,%s,%d\n",
                        escapeCsv(channel.getChannelName()),
                        channel.getRevenue() != null ? channel.getRevenue().toString() : "0",
                        channel.getOrderCount()
                    );
                    outputStream.write(channelRow.getBytes(StandardCharsets.UTF_8));
                }
            }
            
            log.info("CSV export completed successfully");
            return outputStream.toByteArray();
            
        } catch (IOException e) {
            log.error("Error exporting to CSV", e);
            e.printStackTrace();
            throw new RuntimeException("Failed to export report to CSV: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during CSV export", e);
            e.printStackTrace();
            throw new RuntimeException("Unexpected error during CSV export: " + e.getMessage(), e);
        }
    }
    
    /**
     * Placeholder for PDF export.
     * Production implementation would use iText or Apache PDFBox.
     * Returns a text-based placeholder for now.
     */
    public byte[] exportToPdf(ReportGenerationResponse report) {
        log.info("Exporting report to PDF (placeholder): {}", report.getReportType());
        
        StringBuilder pdfContent = new StringBuilder();
        pdfContent.append("REPORT EXPORT - PDF PLACEHOLDER\n");
        pdfContent.append("================================\n\n");
        pdfContent.append("Report Type: ").append(report.getReportType()).append("\n");
        pdfContent.append("Period: ").append(report.getPeriodLabel()).append("\n");
        pdfContent.append("Generated At: ").append(report.getGeneratedAt()).append("\n\n");
        pdfContent.append("SUMMARY\n");
        pdfContent.append("-------\n");
        pdfContent.append("Total Revenue: ").append(report.getTotalRevenue()).append(" IQD\n");
        pdfContent.append("Total Profit: ").append(report.getTotalProfit()).append(" IQD\n");
        pdfContent.append("Total Orders: ").append(report.getTotalOrders()).append("\n");
        pdfContent.append("Accepted Orders: ").append(report.getAcceptedOrders()).append("\n");
        pdfContent.append("Rejected Orders: ").append(report.getRejectedOrders()).append("\n");
        pdfContent.append("Pending Orders: ").append(report.getPendingOrders()).append("\n");
        pdfContent.append("Total Customers: ").append(report.getTotalCustomers()).append("\n");
        pdfContent.append("Average Order Value: ").append(report.getAverageOrderValue()).append(" IQD\n\n");
        
        pdfContent.append("NOTE: This is a text placeholder. Production implementation would use iText or Apache PDFBox for actual PDF generation.\n");
        
        return pdfContent.toString().getBytes(StandardCharsets.UTF_8);
    }
    
    /**
     * Escapes CSV values by wrapping in quotes if they contain commas or quotes.
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
