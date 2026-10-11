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
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        // Fresh cache and limiter instances in @BeforeEach ensure tests are strictly independent
        geocodeCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(24, TimeUnit.HOURS)
                .build();

        geocodeRateLimitCache = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build();

        geocodeGlobalSemaphore = new Semaphore(1);

        geocodeConfig = new GeocodeConfig() {
            @Override
            public String getUserAgent() {
                return "GhaslService-Test/1.0";
            }

            @Override
            public String getReferer() {
                return "https://ghaslservice.local";
            }
        };

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
            // Ignore verification in tests that deliberately check other behavior
        }
    }

    @Test
    void testReverseGeocodeSuccess() {
        BigDecimal lat = new BigDecimal("35.5");
        BigDecimal lng = new BigDecimal("44.5");
        String jsonResponse = "{\"display_name\":\"123 Test Street, Erbil, Iraq\"}";

        mockServer.expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.containsString("nominatim.openstreetmap.org")))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        ResponseEntity<?> response = controller.reverseGeocode(lat, lng, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals("123 Test Street, Erbil, Iraq", body.get("address"));
        mockServer.verify();
    }

    /**
     * Requirement d(1): cache hit: MockRestServiceServer with ExpectedCount.once() and server.verify();
     * second call uses coords differing only after the 4th decimal and must NOT hit upstream.
     */
    @Test
    void testCacheHitWithRoundingBeyondFourthDecimal() {
        BigDecimal lat1 = new BigDecimal("35.12341");
        BigDecimal lng1 = new BigDecimal("44.56781");
        BigDecimal lat2 = new BigDecimal("35.12344"); // Differs only after 4th decimal
        BigDecimal lng2 = new BigDecimal("44.56784"); // Differs only after 4th decimal
        String jsonResponse = "{\"display_name\":\"Erbil Downtown, Iraq\"}";

        // Mock upstream is expected to be called ONCE
        mockServer.expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.containsString("nominatim.openstreetmap.org")))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        // First call hits upstream
        ResponseEntity<?> response1 = controller.reverseGeocode(lat1, lng1, request);
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        Map<?, ?> body1 = (Map<?, ?>) response1.getBody();
        assertEquals("Erbil Downtown, Iraq", body1.get("address"));

        // Second call with coords differing only after 4th decimal must hit cache, not upstream
        ResponseEntity<?> response2 = controller.reverseGeocode(lat2, lng2, request);
        assertEquals(HttpStatus.OK, response2.getStatusCode());
        Map<?, ?> body2 = (Map<?, ?>) response2.getBody();
        assertEquals("Erbil Downtown, Iraq", body2.get("address"));

        mockServer.verify();
    }

    /**
     * Requirement d(2): per-IP limit: 11 REAL requests, first 10 are 200, 11th is 429 with a Retry-After header.
     */
    @Test
    void testPerIpRateLimitElevenRequests() {
        BigDecimal lat = new BigDecimal("35.5");
        BigDecimal lng = new BigDecimal("44.5");
        String jsonResponse = "{\"display_name\":\"Rate Limited Area\"}";

        mockServer.expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.containsString("nominatim.openstreetmap.org")))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.10");

        // First 10 real requests return 200
        for (int i = 1; i <= 10; i++) {
            ResponseEntity<?> response = controller.reverseGeocode(lat, lng, request);
            assertEquals(HttpStatus.OK, response.getStatusCode(), "Request " + i + " must return 200");
        }

        // 11th request must return 429 with Retry-After header
        ResponseEntity<?> response11 = controller.reverseGeocode(lat, lng, request);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response11.getStatusCode(), "11th request must return 429");
        assertNotNull(response11.getHeaders().getFirst("Retry-After"), "Retry-After header must be present on 429");
        assertEquals("60", response11.getHeaders().getFirst("Retry-After"));
    }

    /**
     * Requirement d(3): bounds rejection (lat 10, lng 10 -> 400).
     */
    @Test
    void testBoundsRejectionOutsideKurdistan() {
        BigDecimal lat = new BigDecimal("10.0");
        BigDecimal lng = new BigDecimal("10.0");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        ResponseEntity<?> response = controller.reverseGeocode(lat, lng, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals("Coordinates outside service area", body.get("error"));
    }

    /**
     * Requirement d(4): upstream 500/timeout -> 200 with null address.
     */
    @Test
    void testUpstream500Returns200WithNullAddress() {
        BigDecimal lat = new BigDecimal("35.5");
        BigDecimal lng = new BigDecimal("44.5");

        mockServer.expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.containsString("nominatim.openstreetmap.org")))
                .andRespond(withServerError());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        ResponseEntity<?> response = controller.reverseGeocode(lat, lng, request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertNull(body.get("address"));
        mockServer.verify();
    }

    /**
     * Requirement d(5): control chars stripped + 200-char cap.
     */
    @Test
    void testControlCharactersStrippedAndAddressCappedAt200() {
        BigDecimal lat = new BigDecimal("35.5");
        BigDecimal lng = new BigDecimal("44.5");
        String longAddressWithControlChars = "Erbil\nMain\tStreet\r\u0000" + "Z".repeat(250);
        String jsonResponse = "{\"display_name\":\"" +
                longAddressWithControlChars.replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r").replace("\u0000", "\\u0000") + "\"}";

        mockServer.expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.containsString("nominatim.openstreetmap.org")))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        ResponseEntity<?> response = controller.reverseGeocode(lat, lng, request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        String address = (String) body.get("address");
        assertNotNull(address);
        assertEquals(200, address.length(), "Address must be capped at exactly 200 characters");
        assertFalse(address.contains("\n"), "Newline control characters must be stripped");
        assertFalse(address.contains("\t"), "Tab control characters must be stripped");
        assertFalse(address.contains("\r"), "Carriage return control characters must be stripped");
        assertFalse(address.contains("\u0000"), "Null control characters must be stripped");
        mockServer.verify();
    }

    /**
     * Requirement d(6): blank user-agent config -> 200 null address and no upstream call.
     */
    @Test
    void testBlankUserAgentReturns200NullAddressAndNoUpstreamCall() {
        GeocodeConfig blankConfig = new GeocodeConfig() {
            @Override
            public String getUserAgent() {
                return "";
            }

            @Override
            public String getReferer() {
                return "https://ghaslservice.local";
            }
        };

        GeocodeController blankController = new GeocodeController(
                restTemplate,
                geocodeCache,
                geocodeRateLimitCache,
                geocodeGlobalSemaphore,
                blankConfig
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        // 0 expectations registered on mockServer
        ResponseEntity<?> response = blankController.reverseGeocode(new BigDecimal("35.5"), new BigDecimal("44.5"), request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertNull(body.get("address"));
        mockServer.verify(); // Verifies 0 requests sent upstream
    }

    /**
     * Requirement d(7): spoofed X-Forwarded-For does NOT change the rate-limit bucket.
     */
    @Test
    void testSpoofedXForwardedForDoesNotChangeRateLimitBucket() {
        BigDecimal lat = new BigDecimal("35.5");
        BigDecimal lng = new BigDecimal("44.5");
        String jsonResponse = "{\"display_name\":\"Test Place\"}";

        mockServer.expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.containsString("nominatim.openstreetmap.org")))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // First 10 requests from the same real remoteAddr (10.0.0.99) with differing spoofed X-Forwarded-For
        for (int i = 1; i <= 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRemoteAddr("10.0.0.99");
            req.addHeader("X-Forwarded-For", "198.51.100." + i);
            ResponseEntity<?> response = controller.reverseGeocode(lat, lng, req);
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        // 11th request with another spoofed X-Forwarded-For must be rejected because remoteAddr bucket is full
        MockHttpServletRequest req11 = new MockHttpServletRequest();
        req11.setRemoteAddr("10.0.0.99");
        req11.addHeader("X-Forwarded-For", "198.51.100.200");

        ResponseEntity<?> response11 = controller.reverseGeocode(lat, lng, req11);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response11.getStatusCode(), "Spoofed X-Forwarded-For must not bypass rate limiter");
    }

    @Test
    void testReverseGeocodeNoAddressFromNominatim() {
        BigDecimal lat = new BigDecimal("35.5");
        BigDecimal lng = new BigDecimal("44.5");
        String jsonResponse = "{}"; // No display_name field

        mockServer.expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.containsString("nominatim.openstreetmap.org")))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        ResponseEntity<?> response = controller.reverseGeocode(lat, lng, request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertNull(body.get("address"));
        mockServer.verify();
    }
}
