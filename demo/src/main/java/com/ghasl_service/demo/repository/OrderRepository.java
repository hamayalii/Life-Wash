package com.ghasl_service.demo.repository;

import com.ghasl_service.demo.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

       @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :startDate AND o.createdAt < :endDate")
       long countOrdersBetweenDates(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       @Query("SELECT SUM(COALESCE(o.grandTotal, o.price)) FROM Order o WHERE o.createdAt >= :startDate AND o.createdAt < :endDate")
       BigDecimal sumRevenueBetweenDates(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       @Query("SELECT COUNT(o) FROM Order o WHERE o.price IS NULL " +
                     "AND o.createdAt >= :startDate AND o.createdAt < :endDate")
       long countPendingPriceOrdersBetweenDates(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       /**
        * Revenue excluding REJECTED and PENDING orders — only ACCEPTED orders
        * contribute.
        * Uses COALESCE to support both POS orders (grandTotal) and legacy Lead orders
        * (price).
        */
       @Query("SELECT SUM(COALESCE(o.grandTotal, o.price)) FROM Order o " +
                     "WHERE o.workStatus = com.ghasl_service.demo.model.Order.WorkStatus.ACCEPTED " +
                     "AND (o.grandTotal IS NOT NULL OR o.price IS NOT NULL) " +
                     "AND o.createdAt >= :startDate AND o.createdAt < :endDate")
       BigDecimal sumConfirmedRevenueBetweenDates(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       /**
        * Count orders that are ACCEPTED.
        */
       @Query("SELECT COUNT(o) FROM Order o " +
                     "WHERE o.workStatus = com.ghasl_service.demo.model.Order.WorkStatus.ACCEPTED " +
                     "AND o.createdAt >= :startDate AND o.createdAt < :endDate")
       long countAcceptedOrdersBetweenDates(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       /**
        * Count orders that are REJECTED.
        */
       @Query("SELECT COUNT(o) FROM Order o " +
                     "WHERE o.workStatus = com.ghasl_service.demo.model.Order.WorkStatus.REJECTED " +
                     "AND o.createdAt >= :startDate AND o.createdAt < :endDate")
       long countRejectedOrdersBetweenDates(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       /**
        * Count PENDING orders that still lack a price (wool / pending-admin).
        */
       @Query("SELECT COUNT(o) FROM Order o " +
                     "WHERE o.workStatus = com.ghasl_service.demo.model.Order.WorkStatus.PENDING " +
                     "AND o.price IS NULL " +
                     "AND o.createdAt >= :startDate AND o.createdAt < :endDate")
       long countUnpricedPendingOrdersBetweenDates(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       /**
        * Count orders grouped by service for the donut chart (OrderItem-based, not
        * rugType).
        * Returns Object[] where [0] = service.englishName (String), [1] = count
        * (Long).
        * Uses parameterized query to exclude PENDING orders and avoid hardcoded enum
        * in JPQL.
        * This replaces the legacy rugType-based query for dynamic service
        * architecture.
        */
       @Query("SELECT sc.englishName, COUNT(oi) FROM OrderItem oi " +
                     "JOIN oi.serviceCategory sc " +
                     "JOIN oi.order o " +
                     "WHERE o.createdAt >= :startDate AND o.createdAt < :endDate " +
                     "AND o.workStatus <> :excludedStatus " +
                     "GROUP BY sc.englishName " +
                     "ORDER BY COUNT(oi) DESC")
       List<Object[]> countOrdersByServiceBetweenDates(
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     @Param("excludedStatus") Order.WorkStatus excludedStatus);

       /**
        * Count ALL orders grouped by service for true market demand (Top of Funnel).
        * Returns Object[] where [0] = service.englishName (String), [1] = count
        * (Long).
        * No status filtering - includes PENDING, ACCEPTED, and REJECTED orders.
        * This replaces the legacy rugType-based query for dynamic service
        * architecture.
        */
       @Query("SELECT sc.englishName, COUNT(oi) FROM OrderItem oi " +
                     "JOIN oi.serviceCategory sc " +
                     "JOIN oi.order o " +
                     "WHERE o.createdAt >= :startDate AND o.createdAt < :endDate " +
                     "GROUP BY sc.englishName " +
                     "ORDER BY COUNT(oi) DESC")
       List<Object[]> countAllDemandByServiceBetweenDates(
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       /**
        * Count services by joining OrderItem with ServiceCategory for true market
        * demand.
        * This properly aggregates POS orders that use multi-item relationships.
        * Returns Object[] where [0] = serviceCategory.englishName (String), [1] =
        * count (Long).
        * Groups by ServiceCategory.englishName to show actual service breakdown across
        * all order types.
        * This replaces the legacy Service.name query for dynamic service architecture.
        */
       @Query("SELECT sc.englishName, COUNT(oi) FROM OrderItem oi " +
                     "JOIN oi.serviceCategory sc " +
                     "JOIN oi.order o " +
                     "WHERE o.createdAt >= :startDate AND o.createdAt < :endDate " +
                     "GROUP BY sc.englishName " +
                     "ORDER BY COUNT(oi) DESC")
       List<Object[]> countServicesByOrderItemBetweenDates(
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       /**
        * Fetch ACCEPTED orders for customer session clustering (Q7).
        * Ordered by phoneNumber and createdAt to enable session detection in
        * application code.
        */
       @Query("SELECT o FROM Order o " +
                     "WHERE o.workStatus = com.ghasl_service.demo.model.Order.WorkStatus.ACCEPTED " +
                     "AND o.createdAt >= :startDate AND o.createdAt < :endDate " +
                     "ORDER BY o.phoneNumber, o.createdAt")
       List<Order> findAcceptedOrdersForSessionClustering(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       /**
     * Fetch latest orders for the requests list, ordered by createdAt DESC.
     */
    @EntityGraph(attributePaths = { "items", "items.serviceCategory" })
    @Query("SELECT o FROM Order o ORDER BY o.createdAt DESC")
    List<Order> findLatestOrders(org.springframework.data.domain.Pageable pageable);

    /**
     * Count rejected orders grouped by rejection reason
     * Returns Object[] where [0] = rejectionReason (RejectionReason enum), [1] = count (Long)
     */
    @Query("SELECT o.rejectionReason, COUNT(o) FROM Order o " +
           "WHERE o.workStatus = com.ghasl_service.demo.model.Order.WorkStatus.REJECTED " +
           "AND o.rejectionReason IS NOT NULL " +
           "GROUP BY o.rejectionReason " +
           "ORDER BY COUNT(o) DESC")
    List<Object[]> countRejectedOrdersByReason();

    /**
     * Fetch POS orders (ACCEPTED orders from POS source)
     * For transaction history feature with pagination
     */
    @EntityGraph(attributePaths = { "items", "items.serviceCategory" })
    @Query("SELECT o FROM Order o " +
           "WHERE o.workStatus = com.ghasl_service.demo.model.Order.WorkStatus.ACCEPTED " +
           "AND o.orderSource = com.ghasl_service.demo.model.OrderSource.POS " +
           "ORDER BY o.createdAt DESC")
    org.springframework.data.domain.Page<Order> findPosOrders(org.springframework.data.domain.Pageable pageable);

    /**
     * Sum of grand_total for ACCEPTED orders in a specific period using proper temporal boundaries
     * For monthly profit calculation
     * Uses LocalDateTime parameters to enforce temporal mathematics (Reset to Zero logic)
     */
    @Query("SELECT COALESCE(SUM(COALESCE(o.grandTotal, o.price)), 0) FROM Order o " +
           "WHERE o.workStatus = com.ghasl_service.demo.model.Order.WorkStatus.ACCEPTED " +
           "AND o.createdAt >= :startDate AND o.createdAt < :endDate")
    BigDecimal sumAcceptedOrdersByPeriod(@Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    /**
     * Pareto Analysis Query - Calculates absolute profit and cumulative percentage per service category
     * Uses PostgreSQL Window Functions for single-pass calculation without N+1 queries
     * Returns Object[] where:
     *   [0] = serviceCategory.englishName (String)
     *   [1] = serviceCategory.kurdishName (String)
     *   [2] = absoluteProfit (BigDecimal) - SUM of totalPrice for this service
     *   [3] = cumulativeProfit (BigDecimal) - Running total of profit
     *   [4] = totalProfit (BigDecimal) - Total profit across all services
     *   [5] = cumulativePercentage (BigDecimal) - Cumulative percentage (0-100)
     */
    @Query(value = "SELECT " +
           "sc.english_name, " +
           "sc.kurdish_name, " +
           "SUM(oi.total_price) as absolute_profit, " +
           "SUM(SUM(oi.total_price)) OVER (ORDER BY SUM(oi.total_price) DESC) as cumulative_profit, " +
           "SUM(SUM(oi.total_price)) OVER () as total_profit, " +
           "(SUM(SUM(oi.total_price)) OVER (ORDER BY SUM(oi.total_price) DESC) * 100.0 / " +
           " NULLIF(SUM(SUM(oi.total_price)) OVER (), 0)) as cumulative_percentage " +
           "FROM order_items oi " +
           "JOIN service_categories sc ON oi.category_id = sc.id " +
           "JOIN orders o ON oi.order_id = o.id " +
           "WHERE o.work_status = 'ACCEPTED' " +
           "AND o.created_at >= :startDate " +
           "AND o.created_at < :endDate " +
           "GROUP BY sc.english_name, sc.kurdish_name " +
           "ORDER BY absolute_profit DESC",
           nativeQuery = true)
    List<Object[]> getParetoAnalysisData(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Finds the most recent order date for a customer, excluding a specific order ID.
     * Used for recalculating lastOrderDate when an order is reverted.
     * 
     * @param phoneNumber The customer phone number
     * @param excludeOrderId The order ID to exclude (the reverted order)
     * @return Optional containing the most recent order timestamp, or empty if no orders found
     */
    @Query("SELECT MAX(o.createdAt) FROM Order o WHERE o.phoneNumber = :phoneNumber AND o.id != :excludeOrderId")
    java.util.Optional<LocalDateTime> findLastOrderDateByPhoneNumberExcludingOrderId(
        @Param("phoneNumber") String phoneNumber,
        @Param("excludeOrderId") Long excludeOrderId
    );

    /**
     * Finds all orders between two dates (inclusive start, exclusive end).
     * Used for report generation.
     * 
     * @param startDate Start datetime (inclusive)
     * @param endDate End datetime (exclusive)
     * @return List of orders in the specified date range
     */
    List<Order> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Finds an order by its idempotency key.
     * Used for preventing duplicate order submissions.
     * 
     * @param idempotencyKey The unique idempotency key
     * @return Optional containing the order if found, empty otherwise
     */
    java.util.Optional<Order> findByIdempotencyKey(String idempotencyKey);
}
