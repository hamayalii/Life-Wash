package com.ghasl_service.demo.controller;

import com.ghasl_service.demo.dto.*;
import com.ghasl_service.demo.model.Expense;
import com.ghasl_service.demo.model.ExpenseCategory;
import com.ghasl_service.demo.repository.ExpenseRepository;
import com.ghasl_service.demo.service.ExpenseService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final ExpenseRepository expenseRepository;

    public ExpenseController(ExpenseService expenseService, ExpenseRepository expenseRepository) {
        this.expenseService = expenseService;
        this.expenseRepository = expenseRepository;
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> createExpense(@RequestBody ExpenseRequest request) {
        Expense expense = expenseService.createExpense(request);
        ExpenseResponse response = mapToResponse(expense);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/current-month")
    public ResponseEntity<List<ExpenseResponse>> getCurrentMonthExpenses() {
        List<ExpenseResponse> expenses = expenseService.getCurrentMonthExpenses();
        return ResponseEntity.ok(expenses);
    }

    @GetMapping("/current-month/paginated")
    public ResponseEntity<Page<ExpenseResponse>> getCurrentMonthExpensesPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String currentPeriod = YearMonth.now().toString();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Expense> expensesPage = expenseRepository.findByPeriodOrderByCreatedAtDesc(currentPeriod, pageable);
        
        Page<ExpenseResponse> responsePage = expensesPage.map(this::mapToResponse);
        return ResponseEntity.ok(responsePage);
    }

    @GetMapping("/current-month/total")
    public ResponseEntity<BigDecimal> getCurrentMonthTotal() {
        BigDecimal total = expenseService.getCurrentMonthTotal();
        return ResponseEntity.ok(total);
    }

    @GetMapping("/categories")
    public ResponseEntity<List<ExpenseCategoryResponse>> getCategories() {
        List<ExpenseCategoryResponse> categories = Arrays.stream(ExpenseCategory.values())
                .map(ExpenseCategoryResponse::new)
                .toList();
        return ResponseEntity.ok(categories);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id) {
        if (!expenseRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            expenseRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(409).build(); // Conflict if deletion fails
        }
    }

    private ExpenseResponse mapToResponse(com.ghasl_service.demo.model.Expense expense) {
        ExpenseResponse response = new ExpenseResponse();
        response.setId(expense.getId());
        response.setAmount(expense.getAmount());
        response.setCategory(expense.getCategory());
        response.setPeriod(expense.getPeriod());
        response.setDescription(expense.getDescription());
        response.setCreatedAt(expense.getCreatedAt());
        return response;
    }
}
