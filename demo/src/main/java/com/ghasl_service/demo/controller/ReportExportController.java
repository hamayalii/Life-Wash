package com.ghasl_service.demo.controller;

import com.ghasl_service.demo.dto.ReportGenerationRequest;
import com.ghasl_service.demo.dto.ReportGenerationResponse;
import com.ghasl_service.demo.service.ReportExportService;
import com.ghasl_service.demo.service.ReportGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Controller for exporting reports to various formats (CSV, PDF).
 * Provides endpoints for downloading generated reports.
 */
@RestController
@RequestMapping("/api/v1/admin/reports/export")
public class ReportExportController {
    
    private static final Logger log = LoggerFactory.getLogger(ReportExportController.class);
    
    private final ReportGenerationService reportGenerationService;
    private final ReportExportService reportExportService;
    
    @Autowired
    public ReportExportController(ReportGenerationService reportGenerationService,
                                  ReportExportService reportExportService) {
        this.reportGenerationService = reportGenerationService;
        this.reportExportService = reportExportService;
    }
    
    /**
     * POST /api/v1/admin/reports/export/csv
     * Generates and exports a report to CSV format.
     */
    @PostMapping("/csv")
    public ResponseEntity<?> exportToCsv(@RequestBody ReportGenerationRequest request) {
        try {
            log.info("Exporting {} report to CSV", request != null ? request.getReportType() : "null request");
            
            if (request == null || request.getReportType() == null) {
                log.error("Invalid request: request or reportType is null");
                return ResponseEntity.badRequest().body(
                    java.util.Map.of("error", "Invalid request: reportType is required")
                );
            }
            
            // Generate report data
            ReportGenerationResponse report = reportGenerationService.generateReport(request.getReportType());
            
            if (report == null) {
                log.error("ReportGenerationService returned null report");
                return ResponseEntity.internalServerError().body(
                    java.util.Map.of("error", "Failed to generate report: service returned null")
                );
            }
            
            // Export to CSV
            byte[] csvData = reportExportService.exportToCsv(report);
            
            if (csvData == null || csvData.length == 0) {
                log.error("CSV export returned empty data");
                return ResponseEntity.internalServerError().body(
                    java.util.Map.of("error", "Failed to export report: CSV data is empty")
                );
            }
            
            // Generate filename
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "ghasl_report_" + request.getReportType().toString().toLowerCase() + "_" + timestamp + ".csv";
            
            log.info("CSV export successful: {} bytes, filename: {}", csvData.length, filename);
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(csvData.length)
                .body(csvData);
                
        } catch (Exception e) {
            log.error("Error exporting report to CSV", e);
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                java.util.Map.of("error", "Failed to export report: " + e.getMessage())
            );
        }
    }
    
    /**
     * POST /api/v1/admin/reports/export/pdf
     * Generates and exports a report to PDF format (placeholder).
     */
    @PostMapping("/pdf")
    public ResponseEntity<?> exportToPdf(@RequestBody ReportGenerationRequest request) {
        try {
            log.info("Exporting {} report to PDF (placeholder)", request.getReportType());
            
            // Generate report data
            ReportGenerationResponse report = reportGenerationService.generateReport(request.getReportType());
            
            // Export to PDF (placeholder)
            byte[] pdfData = reportExportService.exportToPdf(report);
            
            // Generate filename
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "ghasl_report_" + request.getReportType().toString().toLowerCase() + "_" + timestamp + ".txt";
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/plain"))
                .contentLength(pdfData.length)
                .body(pdfData);
                
        } catch (Exception e) {
            log.error("Error exporting report to PDF", e);
            return ResponseEntity.internalServerError().body(
                java.util.Map.of("error", "Failed to export report: " + e.getMessage())
            );
        }
    }
}
