package com.ghasl_service.demo.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe IP-based rate limiter using sliding window algorithm.
 * Prevents DoS attacks on unauthenticated endpoints by limiting requests per IP.
 * 
 * Algorithm: Sliding window with 1-minute buckets.
 * Each IP maintains a counter and timestamp of first request in current window.
 * Requests older than 1 minute are discarded.
 */
@Component
public class OrderRateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OrderRateLimitInterceptor.class);

    // Rate limit: 5 requests per minute per IP
    private static final int MAX_REQUESTS_PER_MINUTE = 5;
    private static final Duration WINDOW_SIZE = Duration.ofMinutes(1);

    // Thread-safe storage for IP request tracking
    // Key: Client IP address
    // Value: RateLimitInfo containing request count and window start time
    private final ConcurrentHashMap<String, RateLimitInfo> ipRequestMap = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = getClientIp(request);
        
        if (clientIp == null || clientIp.isEmpty()) {
            log.warn("Unable to determine client IP for rate limiting");
            return true; // Allow request if IP cannot be determined (fail-open for safety)
        }

        Instant now = Instant.now();
        RateLimitInfo info = ipRequestMap.compute(clientIp, (ip, existingInfo) -> {
            if (existingInfo == null) {
                // First request from this IP
                return new RateLimitInfo(1, now);
            }

            // Check if window has expired
            Duration timeSinceFirstRequest = Duration.between(existingInfo.windowStart, now);
            if (timeSinceFirstRequest.compareTo(WINDOW_SIZE) > 0) {
                // Window expired, reset counter
                log.debug("Rate limit window expired for IP: {}, resetting counter", clientIp);
                return new RateLimitInfo(1, now);
            }

            // Increment counter within window
            existingInfo.requestCount.incrementAndGet();
            return existingInfo;
        });

        // Check if rate limit exceeded
        if (info.requestCount.get() > MAX_REQUESTS_PER_MINUTE) {
            log.warn("Rate limit exceeded for IP: {}, request count: {}", clientIp, info.requestCount.get());
            
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            
            String jsonResponse = "{\"status\": 429, \"error\": \"Too Many Requests\", \"message\": \"Rate limit exceeded. Try again in 60 seconds.\"}";
            response.getWriter().write(jsonResponse);
            
            return false;
        }

        log.debug("Rate limit check passed for IP: {}, request count: {}/{}", 
                clientIp, info.requestCount.get(), MAX_REQUESTS_PER_MINUTE);
        return true;
    }

    /**
     * Extracts client IP address from request.
     * Handles proxies and load balancers by checking X-Forwarded-For header.
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // X-Forwarded-For may contain multiple IPs, take the first one
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }

    /**
     * Thread-safe container for rate limit information per IP.
     */
    private static class RateLimitInfo {
        final AtomicInteger requestCount;
        final Instant windowStart;

        RateLimitInfo(int initialCount, Instant windowStart) {
            this.requestCount = new AtomicInteger(initialCount);
            this.windowStart = windowStart;
        }
    }
}
