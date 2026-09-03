package com.ghasl_service.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service for real-time database backup and restore operations.
 * Uses ProcessBuilder to execute pg_dump and psql commands.
 */
@Service
public class BackupService {
    
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    
    @Value("${spring.datasource.url}")
    private String datasourceUrl;
    
    @Value("${spring.datasource.username}")
    private String dbUser;
    
    @Value("${spring.datasource.password}")
    private String dbPassword;
    
    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;
    
    private final DataSource dataSource;
    
    public BackupService(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    /**
     * Extracts database name from JDBC URL
     */
    private String extractDbName() {
        // JDBC URL format: jdbc:postgresql://host:port/database
        try {
            String url = datasourceUrl;
            if (url.startsWith("jdbc:postgresql://")) {
                String[] parts = url.substring("jdbc:postgresql://".length()).split("/");
                if (parts.length > 1) {
                    String dbPart = parts[1].split("\\?")[0]; // Remove query parameters
                    return dbPart;
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract database name from URL", e);
        }
        return "ghasl_db"; // Fallback
    }
    
    /**
     * Extracts host from JDBC URL
     */
    private String extractHost() {
        try {
            String url = datasourceUrl;
            if (url.startsWith("jdbc:postgresql://")) {
                String[] parts = url.substring("jdbc:postgresql://".length()).split("/");
                String hostPort = parts[0];
                String[] hostPortParts = hostPort.split(":");
                return hostPortParts[0];
            }
        } catch (Exception e) {
            log.error("Failed to extract host from URL", e);
        }
        return "localhost"; // Fallback
    }
    
    /**
     * Extracts port from JDBC URL
     */
    private String extractPort() {
        try {
            String url = datasourceUrl;
            if (url.startsWith("jdbc:postgresql://")) {
                String[] parts = url.substring("jdbc:postgresql://".length()).split("/");
                String hostPort = parts[0];
                String[] hostPortParts = hostPort.split(":");
                return hostPortParts.length > 1 ? hostPortParts[1] : "5432";
            }
        } catch (Exception e) {
            log.error("Failed to extract port from URL", e);
        }
        return "5432"; // Fallback
    }
    
    /**
     * Creates a real-time database backup using pg_dump
     * Returns the path to the generated backup file
     */
    public Path createBackup() throws IOException, InterruptedException {
        String dbName = extractDbName();
        String host = extractHost();
        String port = extractPort();
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "ghasl_backup_" + timestamp + ".sql";
        Path backupPath = Files.createTempFile("ghasl_backup_", ".sql");
        
        log.info("Starting real-time backup for database: {} at {}", dbName, backupPath);
        
        List<String> command = new ArrayList<>();
        command.add("pg_dump");
        command.add("-h");
        command.add(host);
        command.add("-p");
        command.add(port);
        command.add("-U");
        command.add(dbUser);
        command.add("-d");
        command.add(dbName);
        command.add("--format=plain");
        command.add("--no-owner");
        command.add("--no-acl");
        command.add("--verbose");
        
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().put("PGPASSWORD", dbPassword);
        processBuilder.redirectOutput(backupPath.toFile());
        
        Process process = processBuilder.start();
        
        // Log error stream separately for debugging
        StringBuilder errorOutput = new StringBuilder();
        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
                log.error("pg_dump error: {}", line);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("pg_dump failed with exit code: {}", exitCode);
            log.error("pg_dump error output: {}", errorOutput.toString());
            Files.deleteIfExists(backupPath);
            throw new IOException("pg_dump failed with exit code: " + exitCode + ", error: " + errorOutput.toString());
        }
        
        log.info("Backup completed successfully: {} (size: {} bytes)", 
                backupPath, Files.size(backupPath));
        
        return backupPath;
    }
    
    /**
     * Terminates all active connections to the database except the current one
     * CRITICAL SAFETY MEASURE before restore operation
     */
    @Transactional
    public void terminateActiveConnections() {
        String dbName = extractDbName();
        
        try {
            String query = "SELECT pg_terminate_backend(pid) " +
                          "FROM pg_stat_activity " +
                          "WHERE datname = '" + dbName + "' " +
                          "AND pid <> pg_backend_pid()";
            
            log.warn("Terminating active connections to database: {}", dbName);
            
            try (var connection = dataSource.getConnection();
                 var statement = connection.createStatement();
                 var resultSet = statement.executeQuery(query)) {
                
                int terminatedCount = 0;
                while (resultSet.next()) {
                    terminatedCount++;
                }
                
                log.info("Terminated {} active connections to database: {}", terminatedCount, dbName);
            }
        } catch (Exception e) {
            log.error("Failed to terminate active connections", e);
            throw new RuntimeException("Failed to terminate active connections: " + e.getMessage(), e);
        }
    }
    
    /**
     * Restores database from a backup file
     * @param backupFile The backup file to restore from
     */
    @Transactional
    public void restoreBackup(File backupFile) throws IOException, InterruptedException {
        String dbName = extractDbName();
        String host = extractHost();
        String port = extractPort();
        
        log.info("Starting restore from backup file: {} to database: {}", backupFile, dbName);
        
        // CRITICAL: Terminate active connections before restore
        terminateActiveConnections();
        
        // Wait a moment for connections to fully terminate
        Thread.sleep(2000);
        
        List<String> command = new ArrayList<>();
        command.add("psql");
        command.add("-h");
        command.add(host);
        command.add("-p");
        command.add(port);
        command.add("-U");
        command.add(dbUser);
        command.add("-d");
        command.add(dbName);
        command.add("-f");
        command.add(backupFile.getAbsolutePath());
        
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().put("PGPASSWORD", dbPassword);
        processBuilder.redirectErrorStream(true);
        
        Process process = processBuilder.start();
        
        // Log output
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("psql: {}", line);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("psql restore failed with exit code: {}", exitCode);
            throw new IOException("psql restore failed with exit code: " + exitCode);
        }
        
        log.info("Restore completed successfully from: {}", backupFile);
    }
    
    /**
     * Restores database from a gzipped backup file
     * @param backupFile The gzipped backup file to restore from
     */
    @Transactional
    public void restoreGzippedBackup(File backupFile) throws IOException, InterruptedException {
        String dbName = extractDbName();
        String host = extractHost();
        String port = extractPort();
        
        log.info("Starting restore from gzipped backup file: {} to database: {}", backupFile, dbName);
        
        // CRITICAL: Terminate active connections before restore
        terminateActiveConnections();
        
        // Wait a moment for connections to fully terminate
        Thread.sleep(2000);
        
        List<String> command = new ArrayList<>();
        command.add("bash");
        command.add("-c");
        command.add("gunzip -c \"" + backupFile.getAbsolutePath() + "\" | psql -h " + host + 
                   " -p " + port + " -U " + dbUser + " -d " + dbName);
        
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().put("PGPASSWORD", dbPassword);
        processBuilder.redirectErrorStream(true);
        
        Process process = processBuilder.start();
        
        // Log output
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("psql: {}", line);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("psql restore from gzip failed with exit code: {}", exitCode);
            throw new IOException("psql restore from gzip failed with exit code: " + exitCode);
        }
        
        log.info("Restore from gzip completed successfully from: {}", backupFile);
    }
    
    /**
     * Scheduled daily backup at 23:00 every day
     * Creates backup to local directory with 7-day retention
     */
    @Scheduled(cron = "0 0 23 * * ?")
    public void scheduledDailyBackup() {
        log.info("Starting scheduled daily backup at {}", LocalDateTime.now());
        
        try {
            String userHome = System.getProperty("user.home");
            Path backupDir = Paths.get(userHome, "ghasl_backups", "daily");
            Files.createDirectories(backupDir);
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "ghasl_daily_" + timestamp + ".sql";
            Path backupPath = backupDir.resolve(filename);
            
            // Create backup using existing logic but to local file
            createBackupToLocal(backupPath);
            
            // Cleanup old backups (keep last 7 days)
            cleanupOldBackups(backupDir, 7);
            
            log.info("Scheduled daily backup completed successfully: {}", backupPath);
            
        } catch (Exception e) {
            log.error("Scheduled daily backup failed", e);
        }
    }
    
    /**
     * Scheduled monthly backup at 23:00 on the last day of every month
     * Creates backup to local directory with 12-month retention
     */
    @Scheduled(cron = "0 0 23 L * ?")
    public void scheduledMonthlyBackup() {
        log.info("Starting scheduled monthly backup at {}", LocalDateTime.now());
        
        try {
            String userHome = System.getProperty("user.home");
            Path backupDir = Paths.get(userHome, "ghasl_backups", "monthly");
            Files.createDirectories(backupDir);
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM_HHmmss"));
            String filename = "ghasl_monthly_" + timestamp + ".sql";
            Path backupPath = backupDir.resolve(filename);
            
            // Create backup using existing logic but to local file
            createBackupToLocal(backupPath);
            
            // Cleanup old backups (keep last 12 months)
            cleanupOldBackups(backupDir, 12);
            
            log.info("Scheduled monthly backup completed successfully: {}", backupPath);
            
        } catch (Exception e) {
            log.error("Scheduled monthly backup failed", e);
        }
    }
    
    /**
     * Creates a backup to a specified local path
     * Reuses the core pg_dump logic from createBackup()
     */
    private void createBackupToLocal(Path backupPath) throws IOException, InterruptedException {
        String dbName = extractDbName();
        String host = extractHost();
        String port = extractPort();
        
        log.info("Creating backup to local path: {} for database: {}", backupPath, dbName);
        
        List<String> command = new ArrayList<>();
        command.add("pg_dump");
        command.add("-h");
        command.add(host);
        command.add("-p");
        command.add(port);
        command.add("-U");
        command.add(dbUser);
        command.add("-d");
        command.add(dbName);
        command.add("--format=plain");
        command.add("--no-owner");
        command.add("--no-acl");
        command.add("--verbose");
        
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().put("PGPASSWORD", dbPassword);
        processBuilder.redirectOutput(backupPath.toFile());
        
        Process process = processBuilder.start();
        
        // Log error stream separately for debugging
        StringBuilder errorOutput = new StringBuilder();
        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
                log.error("pg_dump error: {}", line);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("pg_dump failed with exit code: {}", exitCode);
            log.error("pg_dump error output: {}", errorOutput.toString());
            Files.deleteIfExists(backupPath);
            throw new IOException("pg_dump failed with exit code: " + exitCode + ", error: " + errorOutput.toString());
        }
        
        log.info("Backup created successfully: {} (size: {} bytes)", 
                backupPath, Files.size(backupPath));
    }
    
    /**
     * Cleans up old backup files based on retention policy
     * Keeps the most recent N files, deletes older ones
     * 
     * @param backupDir Directory containing backup files
     * @param retentionCount Number of recent files to keep
     */
    private void cleanupOldBackups(Path backupDir, int retentionCount) {
        try (Stream<Path> stream = Files.list(backupDir)) {
            List<Path> backupFiles = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".sql"))
                .sorted((p1, p2) -> {
                    try {
                        return FileTime.from(Files.getLastModifiedTime(p2).toInstant())
                            .compareTo(FileTime.from(Files.getLastModifiedTime(p1).toInstant()));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .toList();
            
            int totalFiles = backupFiles.size();
            if (totalFiles > retentionCount) {
                int filesToDelete = totalFiles - retentionCount;
                log.info("Cleaning up {} old backup(s) from {} (keeping {})", 
                        filesToDelete, backupDir, retentionCount);
                
                for (int i = retentionCount; i < totalFiles; i++) {
                    Path fileToDelete = backupFiles.get(i);
                    try {
                        Files.delete(fileToDelete);
                        log.info("Deleted old backup: {}", fileToDelete);
                    } catch (IOException e) {
                        log.error("Failed to delete old backup: {}", fileToDelete, e);
                    }
                }
            } else {
                log.info("No cleanup needed for {} ({} files present, retention: {})", 
                        backupDir, totalFiles, retentionCount);
            }
            
        } catch (IOException e) {
            log.error("Failed to cleanup old backups in: {}", backupDir, e);
        }
    }
}
