package com.ghasl_service.demo.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler for REST Controllers.
 * 
 * CRITICAL: Prevents masked 403 errors for 500 crashes by catching exceptions
 * and returning clean JSON responses with appropriate HTTP status codes.
 * 
 * This ensures that Spring Security doesn't intercept internal server errors
 * and return misleading 403 Forbidden responses to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle IllegalStateException - typically thrown when referential integrity is violated.
     * Returns HTTP 500 with clear error message instead of masked 403.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleIllegalStateException(
            IllegalStateException ex, 
            WebRequest request) {
        logger.error("IllegalStateException occurred: {}", ex.getMessage(), ex);
        
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", ex.getMessage());
        body.put("path", request.getDescription(false).replace("uri=", ""));
        
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * Handle IllegalArgumentException - typically thrown for invalid input or validation failures.
     * Returns HTTP 400 with clear error message.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(
            IllegalArgumentException ex, 
            WebRequest request) {
        logger.warn("IllegalArgumentException occurred: {}", ex.getMessage());
        
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", ex.getMessage());
        body.put("path", request.getDescription(false).replace("uri=", ""));
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * Handle NoHandlerFoundException - 404 Not Found for missing endpoints.
     * Returns HTTP 404 without logging as ERROR to reduce noise.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException ex,
            WebRequest request) {
        logger.trace("No handler found for: {}", ex.getRequestURL());

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.NOT_FOUND.value());
        body.put("error", "Not Found");
        body.put("message", "The requested resource was not found");
        body.put("path", request.getDescription(false).replace("uri=", ""));

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * Handle NoResourceFoundException - 404 Not Found for missing static resources (e.g., favicon.ico).
     * Returns HTTP 404 with simple WARN log without full stack trace to reduce log spam.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex,
            WebRequest request) {
        logger.warn("Resource not found: {}", ex.getMessage());

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.NOT_FOUND.value());
        body.put("error", "Not Found");
        body.put("message", "The requested resource was not found");
        body.put("path", request.getDescription(false).replace("uri=", ""));

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * Handle generic Exception - catch-all for unexpected errors.
     * Returns HTTP 500 with actual exception message and full stack trace.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGlobalException(
            Exception ex, 
            WebRequest request) {
        logger.error("Unhandled exception: ", ex);
        
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", ex.getMessage());
        body.put("path", request.getDescription(false).replace("uri=", ""));
        
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * Handle OptimisticLockConflictException - concurrent modification conflicts.
     * Returns HTTP 409 Conflict.
     */
    @ExceptionHandler(OptimisticLockConflictException.class)
    public ResponseEntity<Object> handleOptimisticLockConflict(
            OptimisticLockConflictException ex, 
            WebRequest request) {
        logger.warn("Optimistic lock conflict: {}", ex.getMessage());
        
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", "Conflict");
        body.put("message", "This record was modified by another user. Please refresh and try again.");
        body.put("path", request.getDescription(false).replace("uri=", ""));
        
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * Handle PricingUnavailableException - service pricing not available.
     * Returns HTTP 503 Service Unavailable.
     */
    @ExceptionHandler(PricingUnavailableException.class)
    public ResponseEntity<Object> handlePricingUnavailable(
            PricingUnavailableException ex, 
            WebRequest request) {
        logger.warn("Pricing unavailable: {}", ex.getMessage());
        
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        body.put("error", "Service Unavailable");
        body.put("message", ex.getMessage());
        body.put("path", request.getDescription(false).replace("uri=", ""));
        
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
