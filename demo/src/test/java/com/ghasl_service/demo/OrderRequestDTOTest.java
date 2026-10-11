package com.ghasl_service.demo;

import com.ghasl_service.demo.dto.OrderItemDTO;
import com.ghasl_service.demo.dto.OrderRequestDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OrderRequestDTO validation using Jakarta Validator.
 */
class OrderRequestDTOTest {

    private static Validator validator;

    @BeforeAll
    static void setupValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private OrderRequestDTO createValidDTO() {
        OrderRequestDTO dto = new OrderRequestDTO();
        dto.setCustomerName("Test Customer");
        dto.setPhoneNumber("07501234567");
        dto.setCreatedBy("admin");
        dto.setIdempotencyKey("test-key-123");
        OrderItemDTO item = new OrderItemDTO();
        item.setServiceId(1L);
        item.setQuantity(BigDecimal.ONE);
        item.setUnitName("piece");
        item.setUnitPrice(BigDecimal.valueOf(1000));
        item.setTotalPrice(BigDecimal.valueOf(1000));
        dto.setItems(List.of(item));
        return dto;
    }

    @Test
    void testValidCoordinates() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(new BigDecimal("35.5"));
        dto.setLongitude(new BigDecimal("44.5"));
        dto.setAccuracyMeters(100.0);
        dto.setLocationSource("gps");
        dto.setLocationCapturedAt(Instant.now());
        dto.setFormattedAddress("Test Address");

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "Valid coordinates should pass validation");
    }

    @Test
    void testNullCoordinates() {
        OrderRequestDTO dto = createValidDTO();
        // Coordinates null is valid (optional field)
        dto.setLatitude(null);
        dto.setLongitude(null);

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "Null coordinates should pass validation");
    }

    @Test
    void testInvalidLatitudeTooHigh() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(new BigDecimal("91")); // Invalid (> 90)
        dto.setLongitude(new BigDecimal("44.5"));

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "Latitude > 90 should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("latitude must be <= 90")));
    }

    @Test
    void testInvalidLatitudeTooLow() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(new BigDecimal("-91")); // Invalid (< -90)
        dto.setLongitude(new BigDecimal("44.5"));

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "Latitude < -90 should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("latitude must be >= -90")));
    }

    @Test
    void testInvalidLongitudeTooHigh() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(new BigDecimal("35.5"));
        dto.setLongitude(new BigDecimal("181")); // Invalid (> 180)

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "Longitude > 180 should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("longitude must be <= 180")));
    }

    @Test
    void testInvalidLongitudeTooLow() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(new BigDecimal("35.5"));
        dto.setLongitude(new BigDecimal("-181")); // Invalid (< -180)

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "Longitude < -180 should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("longitude must be >= -180")));
    }

    @Test
    void testLatitudeWithoutLongitude() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(new BigDecimal("35.5"));
        dto.setLongitude(null);

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "Latitude without longitude should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("latitude and longitude must both be provided")));
    }

    @Test
    void testLongitudeWithoutLatitude() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(null);
        dto.setLongitude(new BigDecimal("44.5"));

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "Longitude without latitude should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("latitude and longitude must both be provided")));
    }

    @Test
    void testInvalidLocationSource() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(new BigDecimal("35.5"));
        dto.setLongitude(new BigDecimal("44.5"));
        dto.setLocationSource("invalid"); // Invalid (not "gps" or "manual")

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "Invalid locationSource should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("locationSource must be 'gps' or 'manual'")));
    }

    @Test
    void testValidLocationSourceGps() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(new BigDecimal("35.5"));
        dto.setLongitude(new BigDecimal("44.5"));
        dto.setLocationSource("gps");

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "Valid locationSource 'gps' should pass validation");
    }

    @Test
    void testValidLocationSourceManual() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(new BigDecimal("35.5"));
        dto.setLongitude(new BigDecimal("44.5"));
        dto.setLocationSource("manual");

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "Valid locationSource 'manual' should pass validation");
    }

    @Test
    void testAccuracyNegative() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(new BigDecimal("35.5"));
        dto.setLongitude(new BigDecimal("44.5"));
        dto.setAccuracyMeters(-1.0); // Invalid (< 0)

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "Negative accuracy should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("accuracy must be >= 0")));
    }

    @Test
    void testAccuracyTooHigh() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(new BigDecimal("35.5"));
        dto.setLongitude(new BigDecimal("44.5"));
        dto.setAccuracyMeters(10001.0); // Invalid (> 10000)

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "Accuracy > 10000 should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("accuracy must be <= 10000")));
    }

    @Test
    void testAccuracyValidBounds() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(new BigDecimal("35.5"));
        dto.setLongitude(new BigDecimal("44.5"));
        dto.setAccuracyMeters(0.0); // Valid (lower bound)

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "Accuracy 0 should pass validation");

        dto.setAccuracyMeters(10000.0); // Valid (upper bound)
        violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "Accuracy 10000 should pass validation");
    }

    @Test
    void testOutsideKurdistanCoordinatesAccepted() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(new BigDecimal("10.0"));
        dto.setLongitude(new BigDecimal("10.0"));

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "Coordinates outside Kurdistan (lat 10, lng 10) must be accepted by OrderRequestDTO");
    }

    @Test
    void testFormattedAddress501Fails() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(new BigDecimal("35.5"));
        dto.setLongitude(new BigDecimal("44.5"));
        String longAddress = "A".repeat(501); // Invalid (> 500 chars)
        dto.setFormattedAddress(longAddress);

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "formattedAddress > 500 chars should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("formattedAddress must not exceed 500 characters")));
    }

    @Test
    void testFormattedAddress500Passes() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(new BigDecimal("35.5"));
        dto.setLongitude(new BigDecimal("44.5"));
        String validAddress = "A".repeat(500); // Valid (500 chars)
        dto.setFormattedAddress(validAddress);

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "formattedAddress of 500 chars should pass validation");
    }

    @Test
    void testAddress256Fails() {
        OrderRequestDTO dto = createValidDTO();
        String longAddress = "A".repeat(256); // Invalid (> 255 chars)
        dto.setAddress(longAddress);

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "address > 255 chars should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("address must not exceed 255 characters")));
    }

    @Test
    void testAddress255Passes() {
        OrderRequestDTO dto = createValidDTO();
        String validAddress = "A".repeat(255); // Valid (255 chars)
        dto.setAddress(validAddress);

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "address of 255 chars should pass validation");
    }

    @Test
    void testAllNullLocationPasses() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(null);
        dto.setLongitude(null);
        dto.setAccuracyMeters(null);
        dto.setLocationSource(null);
        dto.setLocationCapturedAt(null);
        dto.setFormattedAddress(null);

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "All-null location fields should pass validation");
    }

    @Test
    void testLocationCapturedAtFuture() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(new BigDecimal("35.5"));
        dto.setLongitude(new BigDecimal("44.5"));
        // 10 minutes in future (invalid - > 5 min tolerance)
        dto.setLocationCapturedAt(Instant.now().plusSeconds(600));

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "LocationCapturedAt > 5 min in future should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("locationCapturedAt cannot be in the future")));
    }

    @Test
    void testLocationCapturedAtTooOld() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(new BigDecimal("35.5"));
        dto.setLongitude(new BigDecimal("44.5"));
        // 25 hours ago (invalid - > 24 hours)
        dto.setLocationCapturedAt(Instant.now().minusSeconds(90000));

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "LocationCapturedAt > 24 hours old should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("locationCapturedAt is too old")));
    }

    @Test
    void testLocationCapturedAtNowPasses() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(new BigDecimal("35.5"));
        dto.setLongitude(new BigDecimal("44.5"));
        dto.setLocationCapturedAt(Instant.now());

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "LocationCapturedAt now should pass validation");
    }

    @Test
    void testLocationCapturedAtValid() {
        OrderRequestDTO dto = createValidDTO();
        dto.setLatitude(new BigDecimal("35.5"));
        dto.setLongitude(new BigDecimal("44.5"));
        // 1 hour ago (valid)
        dto.setLocationCapturedAt(Instant.now().minusSeconds(3600));

        Set<ConstraintViolation<OrderRequestDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "LocationCapturedAt 1 hour ago should pass validation");
    }
}
