package com.ghasl_service.demo.controller;

import com.ghasl_service.demo.config.GeocodeConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * REST controller for reverse geocoding.
 * Proxies requests to Nominatim with caching and rate limiting.
 */
@RestController
@RequestMapping("/api/v1/geocode")
public class GeocodeController {

    private static final Logger log = LoggerFactory.getLogger(GeocodeController.class);

    private static final int PER_IP_LIMIT = 10; // 10 requests per minute per IP
    private static final int CACHE_KEY_PRECISION = 4; // Round to 4 decimals

    // Kurdistan region sanity bounds (wider than service area for flexibility)
    private static final BigDecimal MIN_LAT = new BigDecimal("30");
    private static final BigDecimal MAX_LAT = new BigDecimal("40");
    private static final BigDecimal MIN_LNG = new BigDecimal("40");
    private static final BigDecimal MAX_LNG = new BigDecimal("50");

    private final RestTemplate restTemplate;
    private final com.github.benmanes.caffeine.cache.Cache<String, String> geocodeCache;
    private final com.github.benmanes.caffeine.cache.Cache<String, Integer> geocodeRateLimitCache;
    private final Semaphore geocodeGlobalSemaphore;
    private final GeocodeConfig geocodeConfig;

    public GeocodeController(RestTemplate geocodeRestTemplate,
                             com.github.benmanes.caffeine.cache.Cache<String, String> geocodeCache,
                             com.github.benmanes.caffeine.cache.Cache<String, Integer> geocodeRateLimitCache,
                             Semaphore geocodeGlobalSemaphore,
                             GeocodeConfig geocodeConfig) {
        this.restTemplate = geocodeRestTemplate;
        this.geocodeCache = geocodeCache;
        this.geocodeRateLimitCache = geocodeRateLimitCache;
        this.geocodeGlobalSemaphore = geocodeGlobalSemaphore;
        this.geocodeConfig = geocodeConfig;
    }

    /**
     * GET /api/v1/geocode/reverse?lat=X&lng=Y
     * Returns formatted address for given coordinates.
     * Rate limited: 10 req/min per IP, 1 req/sec globally.
     */
    @GetMapping("/reverse")
    public ResponseEntity<?> reverseGeocode(
            @RequestParam BigDecimal lat,
            @RequestParam BigDecimal lng,
            HttpServletRequest request) {

        // Get client IP directly (no manual proxy header parsing)
        String clientIp = request.getRemoteAddr();

        // Per-IP rate limiting
        Integer ipCount = geocodeRateLimitCache.getIfPresent(clientIp);
        if (ipCount == null) {
            ipCount = 0;
        }
        if (ipCount >= PER_IP_LIMIT) {
            log.warn("Per-IP rate limit exceeded for IP: {}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "60")
                    .body(Map.of("error", "Too many requests from this IP"));
        }
        geocodeRateLimitCache.put(clientIp, ipCount + 1);

        // Validate coordinate bounds (Kurdistan region sanity check)
        if (lat.compareTo(MIN_LAT) < 0 || lat.compareTo(MAX_LAT) > 0 ||
            lng.compareTo(MIN_LNG) < 0 || lng.compareTo(MAX_LNG) > 0) {
            log.warn("Coordinates outside service area: lat={}, lng={}", lat, lng);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Coordinates outside service area"));
        }

        // If User-Agent or Referer is blank, do NOT call Nominatim: return 200 with {"address": null} and log one WARN
        if (geocodeConfig.getUserAgent() == null || geocodeConfig.getUserAgent().isBlank() ||
            geocodeConfig.getReferer() == null || geocodeConfig.getReferer().isBlank()) {
            log.warn("Nominatim User-Agent or Referer is blank; skipping upstream geocoding");
            Map<String, Object> emptyResponse = new java.util.HashMap<>();
            emptyResponse.put("address", null);
            return ResponseEntity.ok(emptyResponse);
        }

        // Round coordinates to 4 decimals for cache key
        String cacheKey = lat.setScale(CACHE_KEY_PRECISION, RoundingMode.HALF_UP) + "," +
                         lng.setScale(CACHE_KEY_PRECISION, RoundingMode.HALF_UP);

        // Check cache
        String cached = geocodeCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("Cache hit for coordinates: {}", cacheKey);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS))
                    .body(Map.of("address", cached));
        }

        // Global rate limiting (1 req/sec)
        try {
            if (!geocodeGlobalSemaphore.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                log.warn("Global rate limit exceeded");
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", "1")
                        .body(Map.of("error", "Too many requests globally"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Service unavailable"));
        }

        try {
            // Call Nominatim
            String url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=" + lat + "&lon=" + lng + "&accept-language=ckb,ku,ar,en";

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", geocodeConfig.getUserAgent());
            headers.set("Referer", geocodeConfig.getReferer());

            org.springframework.http.HttpEntity<?> entity = new org.springframework.http.HttpEntity<>(headers);

            try {
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, 
                        new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

                Map<String, Object> body = response.getBody();
                if (body != null && body.containsKey("display_name")) {
                    String address = (String) body.get("display_name");

                    // Strip control characters
                    address = address.replaceAll("[\\p{Cntrl}]", "");

                    // Cap length at 200 chars
                    if (address.length() > 200) {
                        address = address.substring(0, 200);
                    }

                    // Cache result
                    geocodeCache.put(cacheKey, address);

                    log.debug("Cache miss, fetched and cached address for: {}", cacheKey);
                    return ResponseEntity.ok()
                            .cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS))
                            .body(Map.of("address", address));
                } else {
                    // Nominatim returned no address (e.g., ocean, no data)
                    log.warn("Nominatim returned no address for: {}", cacheKey);
                    Map<String, Object> emptyBody = new java.util.HashMap<>();
                    emptyBody.put("address", null);
                    return ResponseEntity.ok(emptyBody);
                }
            } catch (Exception e) {
                // Nominatim error or network failure - degrade gracefully
                log.error("Nominatim error for coordinates: {}, error: {}", cacheKey, e.getMessage());
                Map<String, Object> response = new java.util.HashMap<>();
                response.put("address", null);
                return ResponseEntity.ok(response);
            }
        } finally {
            geocodeGlobalSemaphore.release();
        }
    }
}
