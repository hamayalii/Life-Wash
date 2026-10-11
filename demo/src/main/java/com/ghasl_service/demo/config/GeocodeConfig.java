package com.ghasl_service.demo.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for reverse geocoding service.
 * Provides RestTemplate, Caffeine cache, and rate limiting.
 */
@Configuration
public class GeocodeConfig {

    @Value("${app.geocode.user-agent:}")
    private String userAgent;

    @Value("${app.geocode.referer:}")
    private String referer;

    /**
     * RestTemplate with configured timeouts.
     * Connect timeout: 3 seconds
     * Read timeout: 5 seconds
     */
    @Bean
    public RestTemplate geocodeRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); // 3 seconds
        factory.setReadTimeout(5000); // 5 seconds
        return new RestTemplate(factory);
    }

    /**
     * Cache for reverse geocoding results.
     * Max size: 1000 entries
     * TTL: 24 hours
     * Key: "lat,lng" rounded to 4 decimals
     * Value: formatted address string
     */
    @Bean
    public Cache<String, String> geocodeCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(24, TimeUnit.HOURS)
                .build();
    }

    /**
     * Per-IP rate limiting cache.
     * Tracks request count per IP address.
     * Max 10 requests per minute per IP.
     */
    @Bean
    public Cache<String, Integer> geocodeRateLimitCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build();
    }

    /**
     * Global rate limiter for Nominatim API.
     * Allows 1 request per second globally.
     * Prevents a single abuser from starving all users.
     */
    @Bean
    public Semaphore geocodeGlobalSemaphore() {
        return new Semaphore(1); // 1 permit = 1 req/sec
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getReferer() {
        return referer;
    }
}
