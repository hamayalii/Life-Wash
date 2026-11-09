package com.ghasl_service.demo.config;

import com.ghasl_service.demo.dto.OrderItemDTO;
import com.ghasl_service.demo.dto.OrderRequestDTO;
import com.ghasl_service.demo.model.Service;
import com.ghasl_service.demo.repository.OrderRepository;
import com.ghasl_service.demo.repository.ServiceRepository;
import com.ghasl_service.demo.service.OrderManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Idempotent demo-data seeder: creates a few sample customer orders on first boot
 * so the admin dashboard is not empty for a demo. Reuses the real order-creation
 * service (no duplicated pricing logic). Runs only when the orders table is empty.
 */
@Component
@Order(3)
public class DemoOrdersSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoOrdersSeeder.class);

    private final OrderRepository orderRepository;
    private final ServiceRepository serviceRepository;
    private final OrderManagementService orderManagementService;

    public DemoOrdersSeeder(OrderRepository orderRepository,
                            ServiceRepository serviceRepository,
                            OrderManagementService orderManagementService) {
        this.orderRepository = orderRepository;
        this.serviceRepository = serviceRepository;
        this.orderManagementService = orderManagementService;
    }

    @Override
    public void run(String... args) {
        if (orderRepository.count() > 0) {
            log.info("Orders table not empty ({} orders). Skipping demo orders seeding.", orderRepository.count());
            return;
        }

        try {
            seedOrder("ئاری کەریم", "07501234567", "سلێمانی، شەقامی سالم",
                    "فەرشی ژووری دانیشتن", "فەرش", new BigDecimal("3.5"), "demo-order-0001");
            seedOrder("سارا محەمەد", "07507654321", "هەولێر، بەختیاری",
                    "دوو بەتانی", "بەتانی", new BigDecimal("2"), "demo-order-0002");
            seedOrder("دلێر عەزیز", "07709876543", "دهۆک، ناوەندی شار",
                    "پەردەی ماڵ", "پەردە", new BigDecimal("6"), "demo-order-0003");
            log.info("Seeded {} demo orders.", orderRepository.count());
        } catch (Exception e) {
            log.error("Failed to seed demo orders (non-fatal, app continues): {}", e.getMessage(), e);
        }
    }

    private void seedOrder(String customerName, String phone, String address, String notes,
                           String serviceName, BigDecimal quantity, String idempotencyKey) {
        Service service = serviceRepository.findAll().stream()
                .filter(s -> serviceName.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Service not found: " + serviceName));

        BigDecimal unitPrice = service.getBasePrice() != null
                ? service.getBasePrice()
                : new BigDecimal("1000");

        OrderItemDTO item = new OrderItemDTO(
                service.getId(),
                quantity,
                null,
                unitPrice,
                unitPrice.multiply(quantity));

        List<OrderItemDTO> items = new ArrayList<>();
        items.add(item);

        OrderRequestDTO request = new OrderRequestDTO(
                customerName, phone, address, notes, "WEBSITE", idempotencyKey, items);

        orderManagementService.createPosOrder(request, true);
        log.info("Seeded demo order for customer '{}'.", customerName);
    }
}
