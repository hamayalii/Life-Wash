package com.ghasl_service.demo.validation;

import com.ghasl_service.demo.dto.OrderRequestDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Duration;
import java.time.Instant;

/**
 * Custom validator for location fields in OrderRequestDTO.
 * Validates:
 * - lat and lng must both be null or both provided
 * - lat/lng global bounds (lat: -90 to 90, lng: -180 to 180) enforced by @DecimalMin/@DecimalMax on DTO
 * - locationCapturedAt must not be in future by >5 minutes
 * - locationCapturedAt must not be older than 24 hours
 */
public class LocationFieldsValidator implements ConstraintValidator<ValidLocationFields, OrderRequestDTO> {

    private static final int FUTURE_TOLERANCE_MINUTES = 5;
    private static final int MAX_AGE_HOURS = 24;

    @Override
    public boolean isValid(OrderRequestDTO value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        java.math.BigDecimal lat = value.getLatitude();
        java.math.BigDecimal lng = value.getLongitude();

        // Latitude and longitude must both be provided or both null
        if ((lat == null && lng != null) || (lat != null && lng == null)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("latitude and longitude must both be provided or both null").addConstraintViolation();
            return false;
        }

        // Validate locationCapturedAt
        Instant capturedAt = value.getLocationCapturedAt();
        if (capturedAt != null) {
            Instant now = Instant.now();
            
            // Not in future by more than 5 minutes
            if (capturedAt.isAfter(now.plus(Duration.ofMinutes(FUTURE_TOLERANCE_MINUTES)))) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("locationCapturedAt cannot be in the future").addConstraintViolation();
                return false;
            }
            
            // Not older than 24 hours
            if (capturedAt.isBefore(now.minus(Duration.ofHours(MAX_AGE_HOURS)))) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("locationCapturedAt is too old").addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}
