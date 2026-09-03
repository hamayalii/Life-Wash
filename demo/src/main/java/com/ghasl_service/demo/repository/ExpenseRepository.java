package com.ghasl_service.demo.repository;

import com.ghasl_service.demo.model.Expense;
import com.ghasl_service.demo.model.ExpenseCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    
    List<Expense> findByPeriodOrderByCreatedAtDesc(String period);
    
    List<Expense> findByPeriodAndCategory(String period, ExpenseCategory category);
    
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.period = :period")
    BigDecimal sumAmountByPeriod(@Param("period") String period);
    
    /**
     * Find expenses by period with pagination support
     */
    Page<Expense> findByPeriodOrderByCreatedAtDesc(String period, Pageable pageable);
}
