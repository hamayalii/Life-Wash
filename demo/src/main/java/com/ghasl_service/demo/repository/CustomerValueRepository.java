package com.ghasl_service.demo.repository;

import com.ghasl_service.demo.model.CustomerValue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for CustomerValue entity.
 * Provides methods for customer lifetime value calculations and customer
 * analytics.
 */
@Repository
public interface CustomerValueRepository extends JpaRepository<CustomerValue, Long> {

    /**
     * Find customer value record by phone number.
     */
    Optional<CustomerValue> findByPhoneNumber(String phoneNumber);

    /**
     * Calculate average customer lifetime value across all customers.
     * Formula: Sum of all customer lifetime values / Total number of customers.
     */
    @Query("SELECT COALESCE(AVG(cv.totalLifetimeValue), 0) FROM CustomerValue cv")
    BigDecimal calculateAverageCLV();

    /**
     * Count customers who placed their first order after a specific date.
     * Used to calculate "New Customers" for CAC calculation.
     */
    @Query("SELECT COUNT(cv) FROM CustomerValue cv WHERE cv.firstOrderDate >= :date")
    Long countCustomersWithFirstOrderAfter(@Param("date") LocalDateTime date);

    /**
     * Count customers who placed their first order within a date range.
     * Used to calculate "New Customers" for a specific period.
     */
    @Query("SELECT COUNT(cv) FROM CustomerValue cv WHERE cv.firstOrderDate BETWEEN :startDate AND :endDate")
    Long countCustomersWithFirstOrderBetween(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find top customers by lifetime value.
     */
    List<CustomerValue> findTopByOrderByTotalLifetimeValueDesc(Pageable pageable);

    /**
     * Get total number of customers in the system.
     */
    @Query("SELECT COUNT(cv) FROM CustomerValue cv")
    Long countTotalCustomers();
}
