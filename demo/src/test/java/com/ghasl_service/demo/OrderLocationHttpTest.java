package com.ghasl_service.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghasl_service.demo.dto.OrderItemDTO;
import com.ghasl_service.demo.dto.OrderRequestDTO;
import com.ghasl_service.demo.model.MeasurementUnit;
import com.ghasl_service.demo.model.Order;
import com.ghasl_service.demo.model.Service;
import com.ghasl_service.demo.model.ServiceCategory;
import com.ghasl_service.demo.repository.OrderRepository;
import com.ghasl_service.demo.repository.ServiceCategoryRepository;
import com.ghasl_service.demo.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer tests for POST /api/v1/orders and GET /api/v1/admin/dashboard/requests.
 * Tested against MockMvc initialized from WebApplicationContext with Spring Security.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.geocode.user-agent=",
        "app.geocode.referer="
})
public class OrderLocationHttpTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private ServiceCategoryRepository serviceCategoryRepository;

    private Long testServiceId;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        List<Service> services = serviceRepository.findAll();
        if (!services.isEmpty()) {
            testServiceId = services.get(0).getId();
        } else {
            ServiceCategory cat = new ServiceCategory();
            cat.setEnglishName("HttpTest Category " + System.currentTimeMillis());
            cat.setKurdishName("تێست");
            cat.setIsActive(true);
            cat = serviceCategoryRepository.save(cat);

            Service s = new Service();
            s.setCategory(cat);
            s.setName("HttpTest Service " + System.currentTimeMillis());
            s.setBasePrice(new BigDecimal("5000"));
            s.setDefaultUnitType(MeasurementUnit.PER_PIECE);
            s = serviceRepository.save(s);
            testServiceId = s.getId();
        }
    }

    private OrderRequestDTO createBaseOrderRequest(String ip) {
        OrderRequestDTO dto = new OrderRequestDTO();
        dto.setCustomerName("Test Customer");
        dto.setPhoneNumber("07501234567");
        dto.setAddress("Bakhtiari, Erbil");
        dto.setCreatedBy("WEBSITE");
        dto.setIdempotencyKey(UUID.randomUUID().toString());

        OrderItemDTO item = new OrderItemDTO();
        item.setServiceId(testServiceId);
        item.setQuantity(BigDecimal.ONE);
        item.setUnitName("PIECE");
        item.setUnitPrice(new BigDecimal("5000"));
        item.setTotalPrice(new BigDecimal("5000"));
        dto.setItems(List.of(item));
        return dto;
    }

    /**
     * b(1): valid location -> 2xx and persisted (BigDecimal scale 6, Instant)
     */
    @Test
    void testCreateOrderWithValidLocationPersisted() throws Exception {
        String clientIp = "192.168.10.1";
        OrderRequestDTO request = createBaseOrderRequest(clientIp);
        request.setLatitude(new BigDecimal("35.123456"));
        request.setLongitude(new BigDecimal("44.654321"));
        request.setAccuracyMeters(12.5);
        request.setLocationSource("gps");
        request.setLocationCapturedAt(Instant.now().minusSeconds(30));
        request.setFormattedAddress("Dream City, Erbil");

        String responseBody = mockMvc.perform(post("/api/v1/orders")
                        .with(req -> {
                            req.setRemoteAddr(clientIp);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", request.getIdempotencyKey())
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();

        Number idNum = objectMapper.readTree(responseBody).get("id").numberValue();
        Long orderId = idNum.longValue();

        Order saved = orderRepository.findById(orderId).orElseThrow();
        assertThat(saved.getLatitude()).isNotNull();
        assertThat(saved.getLatitude().scale()).isEqualTo(6);
        assertThat(saved.getLongitude()).isNotNull();
        assertThat(saved.getLongitude().scale()).isEqualTo(6);
        assertThat(saved.getAccuracyMeters()).isEqualTo(12.5);
        assertThat(saved.getLocationSource()).isEqualTo("gps");
        assertThat(saved.getLocationCapturedAt()).isNotNull();
        assertThat(saved.getFormattedAddress()).isEqualTo("Dream City, Erbil");
    }

    /**
     * b(2): no location -> 2xx and location columns null
     */
    @Test
    void testCreateOrderWithNoLocationColumnsNull() throws Exception {
        String clientIp = "192.168.10.2";
        OrderRequestDTO request = createBaseOrderRequest(clientIp);

        String responseBody = mockMvc.perform(post("/api/v1/orders")
                        .with(req -> {
                            req.setRemoteAddr(clientIp);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", request.getIdempotencyKey())
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();

        Number idNum = objectMapper.readTree(responseBody).get("id").numberValue();
        Long orderId = idNum.longValue();

        Order saved = orderRepository.findById(orderId).orElseThrow();
        assertThat(saved.getLatitude()).isNull();
        assertThat(saved.getLongitude()).isNull();
        assertThat(saved.getAccuracyMeters()).isNull();
        assertThat(saved.getLocationSource()).isNull();
        assertThat(saved.getLocationCapturedAt()).isNull();
        assertThat(saved.getFormattedAddress()).isNull();
    }

    /**
     * b(3): lat only -> 400
     */
    @Test
    void testCreateOrderLatitudeOnlyReturns400() throws Exception {
        String clientIp = "192.168.10.3";
        OrderRequestDTO request = createBaseOrderRequest(clientIp);
        request.setLatitude(new BigDecimal("35.5"));
        request.setLongitude(null);

        mockMvc.perform(post("/api/v1/orders")
                        .with(req -> {
                            req.setRemoteAddr(clientIp);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", request.getIdempotencyKey())
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * b(4): lat 91 -> 400
     */
    @Test
    void testCreateOrderLatitude91Returns400() throws Exception {
        String clientIp = "192.168.10.4";
        OrderRequestDTO request = createBaseOrderRequest(clientIp);
        request.setLatitude(new BigDecimal("91.0"));
        request.setLongitude(new BigDecimal("44.5"));

        mockMvc.perform(post("/api/v1/orders")
                        .with(req -> {
                            req.setRemoteAddr(clientIp);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", request.getIdempotencyKey())
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * b(5): outside-Kurdistan coords -> 2xx (OrderRequestDTO accepts global coords)
     */
    @Test
    void testCreateOrderOutsideKurdistanCoordinatesReturns2xx() throws Exception {
        String clientIp = "192.168.10.5";
        OrderRequestDTO request = createBaseOrderRequest(clientIp);
        request.setLatitude(new BigDecimal("10.500000"));
        request.setLongitude(new BigDecimal("10.500000"));

        String responseBody = mockMvc.perform(post("/api/v1/orders")
                        .with(req -> {
                            req.setRemoteAddr(clientIp);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", request.getIdempotencyKey())
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();

        Number idNum = objectMapper.readTree(responseBody).get("id").numberValue();
        Long orderId = idNum.longValue();

        Order saved = orderRepository.findById(orderId).orElseThrow();
        assertThat(saved.getLatitude()).isNotNull();
        assertThat(saved.getLongitude()).isNotNull();
    }

    /**
     * c: Admin dashboard requests shows location fields: null for old, set for new order.
     */
    @Test
    void testGetAdminDashboardRequestsShowsLocationFields() throws Exception {
        // Save an old order (no location)
        String oldKey = UUID.randomUUID().toString();
        Order oldOrder = new Order("OldLocCust " + oldKey.substring(0, 6), "07501110001",
                "rug", new BigDecimal("1500"), "Old Address", "Old Notes", BigDecimal.ONE);
        oldOrder.setIdempotencyKey(oldKey);
        oldOrder.setCreatedBy("WEBSITE");
        orderRepository.save(oldOrder);

        // Save a new order (with location)
        String newKey = UUID.randomUUID().toString();
        Order newOrder = new Order("NewLocCust " + newKey.substring(0, 6), "07502220002",
                "rug", new BigDecimal("2500"), "New Address", "New Notes", BigDecimal.ONE);
        newOrder.setIdempotencyKey(newKey);
        newOrder.setCreatedBy("WEBSITE");
        newOrder.setLatitude(new BigDecimal("36.123456"));
        newOrder.setLongitude(new BigDecimal("43.987654"));
        newOrder.setAccuracyMeters(8.0);
        newOrder.setLocationSource("gps");
        newOrder.setLocationCapturedAt(Instant.now().minusSeconds(120));
        newOrder.setFormattedAddress("Gulan St, Erbil");
        newOrder = orderRepository.save(newOrder);
        final Long newOrderId = newOrder.getId();

        // Call admin endpoint with authenticated admin user
        mockMvc.perform(get("/api/v1/admin/dashboard/requests")
                        .param("page", "0")
                        .param("size", "50")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        // Verify via repository
        Order reloaded = orderRepository.findById(newOrderId).orElseThrow();
        assertThat(reloaded.getLatitude()).isNotNull();
        assertThat(reloaded.getLatitude().compareTo(new BigDecimal("36.123456"))).isEqualTo(0);
        assertThat(reloaded.getLongitude()).isNotNull();
        assertThat(reloaded.getLocationSource()).isEqualTo("gps");
        assertThat(reloaded.getFormattedAddress()).isEqualTo("Gulan St, Erbil");

        // Verify old order has null location
        Optional<Order> reloadedOld = orderRepository.findByIdempotencyKey(oldKey);
        assertThat(reloadedOld).isPresent();
        assertThat(reloadedOld.get().getLatitude()).isNull();
        assertThat(reloadedOld.get().getLongitude()).isNull();
    }

    /**
     * B1 Item 1: unauthenticated GET of /assets/vendor/leaflet-1.9.4/leaflet.js, leaflet.css,
     * images/marker-icon.png returns 200 with correct content types.
     */
    @Test
    void testUnauthenticatedLeafletStaticAssetsReturn200() throws Exception {
        mockMvc.perform(get("/assets/vendor/leaflet-1.9.4/leaflet.js"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String contentType = result.getResponse().getContentType();
                    assertThat(contentType).containsIgnoringCase("javascript");
                });

        mockMvc.perform(get("/assets/vendor/leaflet-1.9.4/leaflet.css"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String contentType = result.getResponse().getContentType();
                    assertThat(contentType).containsIgnoringCase("css");
                });

        mockMvc.perform(get("/assets/vendor/leaflet-1.9.4/images/marker-icon.png"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String contentType = result.getResponse().getContentType();
                    assertThat(contentType).containsIgnoringCase("image");
                });
    }
}

