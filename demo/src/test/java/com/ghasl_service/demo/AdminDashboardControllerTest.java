package com.ghasl_service.demo;

import com.ghasl_service.demo.controller.AdminDashboardController;
import com.ghasl_service.demo.dto.*;
import com.ghasl_service.demo.model.Order;
import com.ghasl_service.demo.repository.ExpenseRepository;
import com.ghasl_service.demo.repository.LeadRepository;
import com.ghasl_service.demo.repository.MarketingSpendRepository;
import com.ghasl_service.demo.repository.OrderRepository;
import com.ghasl_service.demo.repository.ServiceCategoryRepository;
import com.ghasl_service.demo.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminDashboardControllerTest {

    private AdminDashboardController controller;
    private OrderRepository orderRepository;
    private LeadRepository leadRepository;
    private ReportService reportService;
    private ServiceCategoryRepository serviceCategoryRepository;
    private ExpenseRepository expenseRepository;
    private MarketingSpendRepository marketingSpendRepository;

    @BeforeEach
    void setup() {
        orderRepository = mock(OrderRepository.class);
        leadRepository = mock(LeadRepository.class);
        reportService = mock(ReportService.class);
        serviceCategoryRepository = mock(ServiceCategoryRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        marketingSpendRepository = mock(MarketingSpendRepository.class);
        controller = new AdminDashboardController(orderRepository, leadRepository, reportService, serviceCategoryRepository, expenseRepository, marketingSpendRepository);

        // Mock ServiceCategoryRepository for dynamic service name lookups
        when(serviceCategoryRepository.findByEnglishName(anyString()))
            .thenReturn(Optional.empty()); // Default: not found, will use fallback
    }

    @Test
    @DisplayName("GET /summary returns dashboard summary with session-based customer count")
    void getSummary_returnsSummary() {
        // Mock repository responses
        when(orderRepository.findAcceptedOrdersForSessionClustering(any(), any()))
            .thenReturn(List.of(
                createOrder("07701234567", LocalDateTime.of(2026, 7, 28, 10, 0)),
                createOrder("07701234567", LocalDateTime.of(2026, 7, 28, 10, 10)),
                createOrder("07701234567", LocalDateTime.of(2026, 7, 28, 10, 20))
            ));
        when(leadRepository.countLeadsBetweenDates(any(), any())).thenReturn(7L);
        
        // Mock revenue - return 50000 for all calls (simplified test)
        when(orderRepository.sumConfirmedRevenueBetweenDates(any(), any()))
            .thenReturn(new BigDecimal("50000"));

        DashboardSummaryResponse response = controller.getSummary("week");

        assertThat(response.getCustomers()).isEqualTo(1); // 3 orders, same phone, within 30 min = 1 session
        assertThat(response.getOrders()).isEqualTo(7); // Lead-based count
        assertThat(response.getWeeklyGrowthPercent()).isEqualTo(0.0); // 50000-50000 = 0% growth
        assertThat(response.getMonthlyGrowthPercent()).isEqualTo(0.0); // 50000-50000 = 0% growth
        assertThat(response.getCurrency()).isEqualTo("IQD");
    }

    @Test
    @DisplayName("Q7: Same phone, 3 orders 10 min apart = 1 customer session")
    void q7_samePhone_10MinGap_returnsOneCustomer() {
        when(orderRepository.findAcceptedOrdersForSessionClustering(any(), any()))
            .thenReturn(List.of(
                createOrder("07701234567", LocalDateTime.of(2026, 7, 28, 10, 0)),
                createOrder("07701234567", LocalDateTime.of(2026, 7, 28, 10, 10)),
                createOrder("07701234567", LocalDateTime.of(2026, 7, 28, 10, 20))
            ));

        DashboardSummaryResponse response = controller.getSummary("week");

        assertThat(response.getCustomers()).isEqualTo(1);
    }

    @Test
    @DisplayName("Q7: Same phone, 40 min gap = 2 customer sessions")
    void q7_samePhone_40MinGap_returnsTwoCustomers() {
        when(orderRepository.findAcceptedOrdersForSessionClustering(any(), any()))
            .thenReturn(List.of(
                createOrder("07701234567", LocalDateTime.of(2026, 7, 28, 10, 0)),
                createOrder("07701234567", LocalDateTime.of(2026, 7, 28, 10, 40))
            ));

        DashboardSummaryResponse response = controller.getSummary("week");

        assertThat(response.getCustomers()).isEqualTo(2);
    }

    @Test
    @DisplayName("GET /revenue-trend returns revenue trend data")
    void getRevenueTrend_returnsTrend() {
        when(orderRepository.sumConfirmedRevenueBetweenDates(any(), any())).thenReturn(new BigDecimal("1000"));

        List<RevenueTrendResponse> response = controller.getRevenueTrend("week");

        assertThat(response).hasSize(7); // 7 days in a week
        assertThat(response.get(0).amountIQD()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("GET /top-services returns service breakdown with labels")
    void getTopServices_returnsServiceBreakdown() {
        when(orderRepository.countOrdersByServiceBetweenDates(any(), any(), any()))
            .thenReturn(List.of(
                new Object[]{"CARPET", 10L},
                new Object[]{"RUG", 8L},
                new Object[]{"CURTAINS", 5L}
            ));

        List<TopServiceResponse> response = controller.getTopServices("week");

        assertThat(response).hasSize(3);
        // New structure: rugType (English name), label (Kurdish), count
        // Since ServiceCategory returns Optional.empty(), it falls back to English name
        assertThat(response.get(0).rugType()).isEqualTo("CARPET");
        assertThat(response.get(0).label()).isEqualTo("CARPET"); // Fallback when not found in DB
        assertThat(response.get(0).count()).isEqualTo(10);
    }

    @Test
    @DisplayName("GET /requests returns latest orders with service type labels (paginated)")
    void getRequests_returnsLatestOrders() {
        Order order1 = createOrder("Ali", "07701234567", "rug", LocalDateTime.of(2026, 7, 28, 10, 0));
        Order order2 = createOrder("Karim", "07709876543", "carpet", LocalDateTime.of(2026, 7, 28, 9, 0));

        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> orderPage = new PageImpl<>(List.of(order1, order2), pageable, 2);

        when(orderRepository.findAll(any(Pageable.class)))
            .thenReturn(orderPage);

        Page<RequestResponse> response = controller.getRequests(0, 10);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).customerName()).isEqualTo("Ali");
        // Legacy Lead orders now use translateLegacyRugTypeToKurdish helper
        // "rug" -> "فەرش", "carpet" -> "فەرش" (both map to same Kurdish name)
        assertThat(response.getContent().get(0).serviceNamesFormatted()).isEqualTo("فەرش");
        assertThat(response.getContent().get(1).customerName()).isEqualTo("Karim");
        assertThat(response.getContent().get(1).serviceNamesFormatted()).isEqualTo("فەرش");
    }

    // Helper methods

    private Order createOrder(String phoneNumber, LocalDateTime createdAt) {
        Order order = new Order("Test Customer", phoneNumber, "rug",
            new BigDecimal("1250"), "Address", "Message", new BigDecimal("1.0"));
        order.setWorkStatus(Order.WorkStatus.ACCEPTED);
        order.setCreatedAt(createdAt);
        return order;
    }

    private Order createOrder(String customerName, String phoneNumber, String rugType, LocalDateTime createdAt) {
        Order order = new Order(customerName, phoneNumber, rugType,
            new BigDecimal("1250"), "Address", "Message", new BigDecimal("1.0"));
        order.setWorkStatus(Order.WorkStatus.ACCEPTED);
        order.setCreatedAt(createdAt);
        return order;
    }
}
