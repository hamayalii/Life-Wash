package com.ghasl_service.demo.controller;

import com.ghasl_service.demo.service.BackupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for real-time database backup and restore operations.
 * Provides endpoints for downloading backups and restoring from uploaded files.
 */
@RestController
@RequestMapping("/api/v1/admin/backups")
public class BackupController {
    
    private static final Logger log = LoggerFactory.getLogger(BackupController.class);
    
    private final BackupService backupService;
    
    @Autowired
    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }
    
    /**
     * Endpoint 1: Real-time Download
     * GET /api/v1/admin/backups/download
     * 
     * Uses ProcessBuilder to execute pg_dump dynamically.
     * Pipes output directly to HTTP response OutputStream.
     */
    @GetMapping("/download")
    public ResponseEntity<?> downloadBackup() {
        try {
            log.info("Starting real-time backup download request");
            
            // Create backup using pg_dump via ProcessBuilder
            Path backupPath = backupService.createBackup();
            
            // Generate filename with timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "ghasl_backup_" + timestamp + ".sql";
            
            // Read file bytes
            byte[] fileBytes = Files.readAllBytes(backupPath);
            
            // Clean up temp file
            Files.deleteIfExists(backupPath);
            
            log.info("Backup download completed: {} bytes", fileBytes.length);
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(fileBytes.length)
                .body(fileBytes);
                
        } catch (IOException e) {
            log.error("IO error during backup download", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to create backup: " + e.getMessage())
            );
        } catch (InterruptedException e) {
            log.error("Backup process interrupted", e);
            Thread.currentThread().interrupt();
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Backup process interrupted: " + e.getMessage())
            );
        } catch (Exception e) {
            log.error("Unexpected error during backup download", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Unexpected error: " + e.getMessage())
            );
        }
    }
    
    /**
     * Endpoint 2: Real-time Restore
     * POST /api/v1/admin/backups/restore
     * 
     * Accepts a MultipartFile (.sql or .sql.gz).
     * CRITICAL SAFETY MEASURE: Terminates active connections before restore.
     * Executes restore process to completely replace current data.
     */
    @PostMapping("/restore")
    public ResponseEntity<?> restoreBackup(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "No file provided")
            );
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || 
            (!originalFilename.endsWith(".sql") && !originalFilename.endsWith(".sql.gz"))) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "Invalid file type. Only .sql and .sql.gz files are supported")
            );
        }
        
        log.info("Starting restore from uploaded file: {} (size: {} bytes)", 
                originalFilename, file.getSize());
        
        try {
            // Create temp file from uploaded multipart
            Path tempPath = Files.createTempFile("ghasl_restore_", 
                    originalFilename.endsWith(".sql.gz") ? ".sql.gz" : ".sql");
            file.transferTo(tempPath.toFile());
            
            File backupFile = tempPath.toFile();
            
            // Execute restore based on file type
            if (originalFilename.endsWith(".sql.gz")) {
                backupService.restoreGzippedBackup(backupFile);
            } else {
                backupService.restoreBackup(backupFile);
            }
            
            // Clean up temp file
            Files.deleteIfExists(tempPath);
            
            log.info("Restore completed successfully from: {}", originalFilename);
            
            return ResponseEntity.ok(
                Map.of(
                    "message", "Database restored successfully",
                    "filename", originalFilename,
                    "timestamp", LocalDateTime.now().toString()
                )
            );
            
        } catch (IOException e) {
            log.error("IO error during restore", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to restore database: " + e.getMessage())
            );
        } catch (InterruptedException e) {
            log.error("Restore process interrupted", e);
            Thread.currentThread().interrupt();
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Restore process interrupted: " + e.getMessage())
            );
        } catch (Exception e) {
            log.error("Unexpected error during restore", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Unexpected error: " + e.getMessage())
            );
        }
    }
}
