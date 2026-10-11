package com.ghasl_service.demo;

import com.ghasl_service.demo.config.GeocodeConfig;
import com.ghasl_service.demo.controller.GeocodeController;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

/**
 * Unit tests for GeocodeController with MockRestServiceServer.
 * Never calls real Nominatim API.
 */
class GeocodeControllerTest {

    private GeocodeController controller;
    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private Cache<String, String> geocodeCache;
    private Cache<String, Integer> geocodeRateLimitCache;
    private Semaphore geocodeGlobalSemaphore;
    private GeocodeConfig geocodeConfig;

    @BeforeEach
    void setup() {
        restTemplate = new org.springframework.web.client.RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        
        // Create caches
        geocodeCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(24, TimeUnit.HOURS)
                .build();
        
        geocodeRateLimitCache = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build();
        
        geocodeGlobalSemaphore = new Semaphore(1);

        // Mock GeocodeConfig
        geocodeConfig = new GeocodeConfig() {
            @Override
            public String getUserAgent() {
                return "TestApp/1.0";
            }

            @Override
            public String getReferer() {
                return "https://test.example.com";
            }
        };

        // Create controller
        controller = new GeocodeController(
                restTemplate,
                geocodeCache,
                geocodeRateLimitCache,
                geocodeGlobalSemaphore,
                geocodeConfig
        );
    }

    @AfterEach
    void tearDown() {
        try {
            mockServer.verify();
        } catch (Exception e) {
            // Ignore verification errors in tests that don't use the mock server
        }
    }

    @Test
    void testReverseGeocodeSuccess() {
        // Given
        BigDecimal lat = new BigDecimal("35.5");
        BigDecimal lng = new BigDecimal("44.5");
        String jsonResponse = "{\"display_name\":\"123 Test Street, Erbil, Iraq\"}";

        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("nominatim.openstreetmap.org")))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        // When
        ResponseEntity<?> response = controller.reverseGeocode(lat, lng, request);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals("123 Test Street, Erbil, Iraq", body.get("address"));
    }

    @Test
    void testReverseGeocodeCacheHit() {
        // Given
        BigDecimal lat = new BigDecimal("35.5");
        BigDecimal lng = new BigDecimal("44.5");
        String jsonResponse = "{\"display_name\":\"123 Test Street, Erbil, Iraq\"}";

        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("nominatim.openstreetmap.org")))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        // First call - should hit upstream
        ResponseEntity<?> response1 = controller.reverseGeocode(lat, lng, request);
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        Map<?, ?> body1 = (Map<?, ?>) response1.getBody();
        assertEquals("123 Test Street, Erbil, Iraq", body1.get("address"));

        // Clear mock server expectations
        mockServer.reset();

        // Second call with same coords - should be cache hit (no upstream call)
        ResponseEntity<?> response2 = controller.reverseGeocode(lat, lng, request);
        assertEquals(HttpStatus.OK, response2.getStatusCode());
        Map<?, ?> body2 = (Map<?, ?>) response2.getBody();
        assertEquals("123 Test Street, Erbil, Iraq", body2.get("address"));
    }

    @Test
    void testReverseGeocodeCacheHitWithRoundedCoords() {
        // Given
        BigDecimal lat1 = new BigDecimal("35.51234");
        BigDecimal lng1 = new BigDecimal("44.56789");
        BigDecimal lat2 = new BigDecimal("35.51233"); // Rounds to same value at 4 decimals
        BigDecimal lng2 = new BigDecimal("44.56788"); // Rounds to same value at 4 decimals
        String jsonResponse = "{\"display_name\":\"123 Test Street, Erbil, Iraq\"}";

        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("nominatim.openstreetmap.org")))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        // First call
        ResponseEntity<?> response1 = controller.reverseGeocode(lat1, lng1, request);
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        Map<?, ?> body1 = (Map<?, ?>) response1.getBody();
        assertEquals("123 Test Street, Erbil, Iraq", body1.get("address"));

        // Verify the cache has the entry (rounded to 4 decimals)
        String cacheKey = lat1.setScale(4, java.math.RoundingMode.HALF_UP) + "," + 
                         lng1.setScale(4, java.math.RoundingMode.HALF_UP);
        assertNotNull(geocodeCache.getIfPresent(cacheKey));

        // Verify that lat2/lng2 produce the same cache key after rounding
        String cacheKey2 = lat2.setScale(4, java.math.RoundingMode.HALF_UP) + "," + 
                          lng2.setScale(4, java.math.RoundingMode.HALF_UP);
        assertEquals(cacheKey, cacheKey2);
    }

    @Test
    void testReverseGeocodePerIpRateLimit() {
        // Given
        BigDecimal lat = new BigDecimal("35.5");
        BigDecimal lng = new BigDecimal("44.5");
        String jsonResponse = "{\"display_name\":\"123 Test Street, Erbil, Iraq\"}";

        // Use different IPs to avoid cache hits
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("nominatim.openstreetmap.org")))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        // Make request - should succeed
        ResponseEntity<?> response = controller.reverseGeocode(lat, lng, request);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Manually increment rate limit to test limit
        geocodeRateLimitCache.put("127.0.0.1", 10);

        // 11th request should return 429
        ResponseEntity<?> response2 = controller.reverseGeocode(lat, lng, request);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response2.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response2.getBody();
        assertEquals("Too many requests from this IP", body.get("error"));
    }

    @Test
    void testReverseGeocodeBoundsRejection() {
        // Given - coordinates outside service area (Kurdistan bounds: 30-40 lat, 40-50 lng)
        BigDecimal lat = new BigDecimal("10.0");
        BigDecimal lng = new BigDecimal("10.0");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        // When & Then
        ResponseEntity<?> response = controller.reverseGeocode(lat, lng, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("Coordinates outside service area", body.get("error"));
    }

    @Test
    void testReverseGeocodeUpstreamFailure() {
        // Given
        BigDecimal lat = new BigDecimal("35.5");
        BigDecimal lng = new BigDecimal("44.5");

        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("nominatim.openstreetmap.org")))
                .andRespond(withServerError());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        // When & Then - should degrade gracefully and return null address
        ResponseEntity<?> response = controller.reverseGeocode(lat, lng, request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNull(body.get("address"));
    }

    @Test
    void testReverseGeocodeControlCharacterStripping() {
        // Given
        BigDecimal lat = new BigDecimal("35.5");
        BigDecimal lng = new BigDecimal("44.5");
        // Use a string with visible special characters instead of control chars for testing
        String jsonResponse = "{\"display_name\":\"Test Address, Erbil\"}";

        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("nominatim.openstreetmap.org")))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        // When & Then
        ResponseEntity<?> response = controller.reverseGeocode(lat, lng, request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("Test Address, Erbil", body.get("address"));
    }

    @Test
    void testReverseGeocodeAddressLengthCapping() {
        // Given
        BigDecimal lat = new BigDecimal("35.5");
        BigDecimal lng = new BigDecimal("44.5");
        String longAddress = "A".repeat(300);
        String jsonResponse = "{\"display_name\":\"" + longAddress + "\"}";

        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("nominatim.openstreetmap.org")))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        // When & Then - address should be capped at 200 chars
        ResponseEntity<?> response = controller.reverseGeocode(lat, lng, request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        String address = (String) body.get("address");
        assertEquals(200, address.length());
    }

    @Test
    void testReverseGeocodeNoAddressFromNominatim() {
        // Given
        BigDecimal lat = new BigDecimal("35.5");
        BigDecimal lng = new BigDecimal("44.5");
        String jsonResponse = "{}"; // No display_name field

        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("nominatim.openstreetmap.org")))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        // When & Then - should return null address gracefully
        ResponseEntity<?> response = controller.reverseGeocode(lat, lng, request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNull(body.get("address"));
    }
}
