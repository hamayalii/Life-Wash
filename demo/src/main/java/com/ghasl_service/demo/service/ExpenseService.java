package com.ghasl_service.demo.service;

import com.ghasl_service.demo.dto.ExpenseRequest;
import com.ghasl_service.demo.dto.ExpenseResponse;
import com.ghasl_service.demo.model.Expense;
import com.ghasl_service.demo.model.ExpenseCategory;
import com.ghasl_service.demo.repository.ExpenseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);
    private final ExpenseRepository expenseRepository;

    public ExpenseService(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    @Transactional
    public Expense createExpense(ExpenseRequest request) {
        Expense expense = new Expense();
        expense.setAmount(request.getAmount());
        expense.setCategory(request.getCategory());
        expense.setDescription(request.getDescription());
        
        // Set period to current month if not provided
        YearMonth currentPeriod = YearMonth.now();
        expense.setPeriod(currentPeriod.toString());
        
        log.info("Creating expense: {} - {} IQD for period {}", 
                request.getCategory(), request.getAmount(), expense.getPeriod());
        
        return expenseRepository.save(expense);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getCurrentMonthExpenses() {
        YearMonth currentPeriod = YearMonth.now();
        List<Expense> expenses = expenseRepository.findByPeriodOrderByCreatedAtDesc(currentPeriod.toString());
        
        return expenses.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BigDecimal getCurrentMonthTotal() {
        YearMonth currentPeriod = YearMonth.now();
        BigDecimal total = expenseRepository.sumAmountByPeriod(currentPeriod.toString());
        return total != null ? total : BigDecimal.ZERO;
    }

    private ExpenseResponse mapToResponse(Expense expense) {
        ExpenseResponse response = new ExpenseResponse();
        response.setId(expense.getId());
        response.setAmount(expense.getAmount());
        response.setCategory(expense.getCategory());
        response.setCategoryLabel(expense.getCategory() != null ? expense.getCategory().getKurdishTranslation() : "");
        response.setPeriod(expense.getPeriod());
        response.setDescription(expense.getDescription());
        response.setCreatedAt(expense.getCreatedAt());
        return response;
    }
}
