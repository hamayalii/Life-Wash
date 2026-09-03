package com.ghasl_service.demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghasl_service.demo.model.IdempotencyKey;
import com.ghasl_service.demo.repository.IdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyKeyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Optional<IdempotencyKey> checkAndRecord(String idempotencyKey, Object requestBody) {
        // Check if key exists
        Optional<IdempotencyKey> existing = repository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            log.info("Idempotency key found: {}", idempotencyKey);
            return existing; // Return cached response
        }

        // Create new record
        try {
            IdempotencyKey newKey = new IdempotencyKey();
            newKey.setIdempotencyKey(idempotencyKey);
            newKey.setRequestHash(computeHash(requestBody));
            newKey.setCreatedAt(LocalDateTime.now());
            newKey.setExpiresAt(LocalDateTime.now().plusHours(24));

            repository.save(newKey);
            log.info("Created new idempotency key: {}", idempotencyKey);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Root cause of idempotency key creation failure:", e);
            e.printStackTrace();
            throw new RuntimeException("Failed to create idempotency key", e);
        }
    }

    @Transactional
    public void storeResponse(String idempotencyKey, Object response) {
        try {
            IdempotencyKey key = repository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalArgumentException("Idempotency key not found"));

            key.setResponseData(objectMapper.writeValueAsString(response));
            repository.save(key);
            log.info("Stored response for idempotency key: {}", idempotencyKey);
        } catch (Exception e) {
            // Log error but don't fail the request
            log.error("Failed to store response for idempotency key: {}", idempotencyKey, e);
        }
    }

    private String computeHash(Object obj) {
        try {
            String json = objectMapper.writeValueAsString(obj);
            log.debug("JSON for hash computation: {}", json);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Root cause of idempotency hash failure:", e);
            e.printStackTrace();
            throw new RuntimeException("Failed to compute hash", e);
        }
    }

    @Transactional
    public int cleanupExpiredKeys() {
        int deleted = repository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.info("Cleaned up {} expired idempotency keys", deleted);
        return deleted;
    }

    /**
     * Scheduled cleanup job - runs daily at 3 AM
     * Removes expired idempotency keys to prevent table bloat
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledCleanupExpiredKeys() {
        int deleted = cleanupExpiredKeys();
        log.info("Scheduled cleanup completed: {} keys removed", deleted);
    }
}
