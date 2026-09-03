package com.ghasl_service.demo.config;

import com.ghasl_service.demo.model.Order;
import com.ghasl_service.demo.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data migration to fix existing POS orders that were saved as PENDING.
 * POS orders (identified by having OrderItems) should be ACCEPTED by default
 * as they are direct in-person cashier transactions.
 */
@Component
public class PosOrderDataMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PosOrderDataMigration.class);

    private final OrderRepository orderRepository;

    public PosOrderDataMigration(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Find all PENDING orders that have OrderItems (indicating POS orders)
        // These should be auto-accepted as they are direct in-person transactions
        long migratedCount = orderRepository.findAll().stream()
            .filter(order -> order.getWorkStatus() == Order.WorkStatus.PENDING)
            .filter(order -> order.getItems() != null && !order.getItems().isEmpty())
            .peek(order -> {
                order.setWorkStatus(Order.WorkStatus.ACCEPTED);
                log.info("Migrating POS order ID {} from PENDING to ACCEPTED", order.getId());
            })
            .count();

        if (migratedCount > 0) {
            orderRepository.saveAll(
                orderRepository.findAll().stream()
                    .filter(order -> order.getWorkStatus() == Order.WorkStatus.PENDING)
                    .filter(order -> order.getItems() != null && !order.getItems().isEmpty())
                    .peek(order -> order.setWorkStatus(Order.WorkStatus.ACCEPTED))
                    .toList()
            );
            log.info("Successfully migrated {} POS orders from PENDING to ACCEPTED", migratedCount);
        } else {
            log.info("No POS orders requiring migration found.");
        }
    }
}
