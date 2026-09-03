package com.ghasl_service.demo.repository;

import com.ghasl_service.demo.model.RugWashLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LeadRepository extends JpaRepository<RugWashLead, Long> {

    /** Total leads (all rug types) within a date range. */
    @Query("SELECT COUNT(l) FROM RugWashLead l WHERE l.createdAt >= :startDate AND l.createdAt < :endDate")
    long countLeadsBetweenDates(@Param("startDate") LocalDateTime startDate,
                                @Param("endDate") LocalDateTime endDate);

    /**
     * Returns each rugType and its lead count, ordered by count descending.
     * Used for the rug-type breakdown in reports.
     */
    @Query("SELECT l.rugType, COUNT(l) FROM RugWashLead l " +
           "WHERE l.createdAt >= :startDate AND l.createdAt < :endDate " +
           "GROUP BY l.rugType ORDER BY COUNT(l) DESC")
    List<Object[]> countLeadsByRugTypeBetweenDates(@Param("startDate") LocalDateTime startDate,
                                                   @Param("endDate") LocalDateTime endDate);
}
