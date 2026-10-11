package com.ghasl_service.demo;

import com.ghasl_service.demo.dto.OrderItemDTO;
import com.ghasl_service.demo.dto.OrderRequestDTO;
import com.ghasl_service.demo.model.Order;
import com.ghasl_service.demo.repository.OrderRepository;
import com.ghasl_service.demo.repository.OutboxEventRepository;
import com.ghasl_service.demo.repository.ServiceCategoryRepository;
import com.ghasl_service.demo.repository.ServicePricingRepository;
import com.ghasl_service.demo.repository.ServiceRepository;
import com.ghasl_service.demo.service.OrderManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for OrderManagementService with location fields.
 */
class OrderManagementServiceTest {

    private OrderManagementService service;
    private OrderRepository orderRepository;
    private ServiceRepository serviceRepository;
    private ServiceCategoryRepository serviceCategoryRepository;
    private ServicePricingRepository servicePricingRepository;
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void setup() {
        orderRepository = mock(OrderRepository.class);
        serviceRepository = mock(ServiceRepository.class);
        serviceCategoryRepository = mock(ServiceCategoryRepository.class);
        servicePricingRepository = mock(ServicePricingRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);

        service = new OrderManagementService(
                orderRepository,
                serviceRepository,
                serviceCategoryRepository,
                servicePricingRepository,
                outboxEventRepository
        );
    }

    private OrderRequestDTO createValidRequest() {
        OrderRequestDTO request = new OrderRequestDTO();
        request.setCustomerName("Test Customer");
        request.setPhoneNumber("07501234567");
        request.setAddress("Test Address");
        request.setNotes("Test Notes");
        request.setCreatedBy("admin");
        request.setIdempotencyKey("test-key-123");
        OrderItemDTO item = new OrderItemDTO();
        item.setServiceId(1L);
        item.setQuantity(BigDecimal.ONE);
        item.setUnitName("piece");
        item.setUnitPrice(BigDecimal.valueOf(1000));
        item.setTotalPrice(BigDecimal.valueOf(1000));
        request.setItems(List.of(item));
        return request;
    }

    private void mockServiceFetch() {
        com.ghasl_service.demo.model.Service mockService = mock(com.ghasl_service.demo.model.Service.class);
        com.ghasl_service.demo.model.ServiceCategory mockCategory = mock(com.ghasl_service.demo.model.ServiceCategory.class);
        
        when(mockService.getCategory()).thenReturn(mockCategory);
        when(mockCategory.getId()).thenReturn(1L);
        
        when(serviceRepository.findAllById(any())).thenReturn(List.of(mockService));
        when(serviceRepository.findById(any())).thenReturn(Optional.of(mockService));
        when(serviceCategoryRepository.findById(any())).thenReturn(Optional.of(mockCategory));
    }

    @Test
    void testCreateOrderWithLocation() {
        // Given
        OrderRequestDTO request = createValidRequest();
        request.setLatitude(new BigDecimal("35.5"));
        request.setLongitude(new BigDecimal("44.5"));
        request.setAccuracyMeters(100.0);
        request.setLocationSource("gps");
        request.setLocationCapturedAt(Instant.now());
        request.setFormattedAddress("123 Test Street, Erbil");

        when(orderRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        mockServiceFetch();

        Order savedOrder = new Order();
        savedOrder.setId(1L);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1L);
            return order;
        });

        // When
        Order result = service.createPosOrder(request, false);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getLatitude()).isEqualTo(new BigDecimal("35.5"));
        assertThat(result.getLongitude()).isEqualTo(new BigDecimal("44.5"));
        assertThat(result.getAccuracyMeters()).isEqualTo(100.0);
        assertThat(result.getLocationSource()).isEqualTo("gps");
        assertThat(result.getLocationCapturedAt()).isNotNull();
        assertThat(result.getFormattedAddress()).isEqualTo("123 Test Street, Erbil");
    }

    @Test
    void testCreateOrderWithoutLocation() {
        // Given
        OrderRequestDTO request = createValidRequest();
        // No location fields (should be null)

        when(orderRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        mockServiceFetch();

        Order savedOrder = new Order();
        savedOrder.setId(1L);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1L);
            return order;
        });

        // When
        Order result = service.createPosOrder(request, false);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getLatitude()).isNull();
        assertThat(result.getLongitude()).isNull();
        assertThat(result.getAccuracyMeters()).isNull();
        assertThat(result.getLocationSource()).isNull();
        assertThat(result.getLocationCapturedAt()).isNull();
        assertThat(result.getFormattedAddress()).isNull();
    }

    @Test
    void testFormattedAddressControlCharacterStripping() {
        // Given
        OrderRequestDTO request = createValidRequest();
        request.setLatitude(new BigDecimal("35.5"));
        request.setLongitude(new BigDecimal("44.5"));
        request.setFormattedAddress("Test\u0000Address\u001B"); // Contains control chars

        when(orderRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        mockServiceFetch();

        Order savedOrder = new Order();
        savedOrder.setId(1L);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1L);
            return order;
        });

        // When
        Order result = service.createPosOrder(request, false);

        // Then - control characters should be stripped
        assertThat(result.getFormattedAddress()).isEqualTo("TestAddress");
        assertThat(result.getFormattedAddress()).doesNotContain("\u0000");
        assertThat(result.getFormattedAddress()).doesNotContain("\u001B");
    }

    @Test
    void testLocationFieldsPersistedWithCorrectScale() {
        // Given
        OrderRequestDTO request = createValidRequest();
        request.setLatitude(new BigDecimal("35.123456789")); // More than 6 decimals
        request.setLongitude(new BigDecimal("44.987654321")); // More than 6 decimals
        request.setLocationCapturedAt(Instant.now());

        when(orderRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        mockServiceFetch();

        Order savedOrder = new Order();
        savedOrder.setId(1L);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1L);
            // Simulate database scaling (database would handle this in real scenario)
            order.setLatitude(order.getLatitude().setScale(6, RoundingMode.HALF_UP));
            order.setLongitude(order.getLongitude().setScale(6, RoundingMode.HALF_UP));
            return order;
        });

        // When
        Order result = service.createPosOrder(request, false);

        // Then - should be persisted with scale 6 (simulated by mock)
        assertThat(result.getLatitude()).isEqualTo(new BigDecimal("35.123457").setScale(6, RoundingMode.HALF_UP));
        assertThat(result.getLongitude()).isEqualTo(new BigDecimal("44.987654").setScale(6, RoundingMode.HALF_UP));
        assertThat(result.getLocationCapturedAt()).isNotNull();
    }
}
