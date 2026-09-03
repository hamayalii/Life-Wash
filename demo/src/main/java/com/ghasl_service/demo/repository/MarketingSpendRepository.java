package com.ghasl_service.demo.repository;

import com.ghasl_service.demo.model.MarketingChannel;
import com.ghasl_service.demo.model.MarketingSpend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repository for MarketingSpend entity.
 * Provides methods for querying marketing/advertising expenses by period and
 * channel. Period is stored as String in "yyyy-MM" format.
 */
@Repository
public interface MarketingSpendRepository extends JpaRepository<MarketingSpend, Long> {

    /**
     * Find all marketing spend records for a specific period, ordered by channel.
     */
    List<MarketingSpend> findByPeriodOrderByChannelAsc(String period);

    /**
     * Calculate total marketing spend for a specific period.
     */
    @Query("SELECT COALESCE(SUM(m.amount), 0) FROM MarketingSpend m WHERE m.period = :period")
    BigDecimal sumAmountByPeriod(@Param("period") String period);

    /**
     * Find marketing spend records for a specific period and channel.
     * Returns List to handle potential duplicate records from earlier bugs.
     */
    List<MarketingSpend> findByPeriodAndChannel(String period, MarketingChannel channel);

    /**
     * Check if any marketing spend exists for a period.
     */
    boolean existsByPeriod(String period);
}
