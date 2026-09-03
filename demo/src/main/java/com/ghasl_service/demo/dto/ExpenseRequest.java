package com.ghasl_service.demo.dto;

import com.ghasl_service.demo.model.ExpenseCategory;
import java.math.BigDecimal;

public class ExpenseRequest {
    private BigDecimal amount;
    private ExpenseCategory category;
    private String description;

    // Getters and Setters
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public ExpenseCategory getCategory() { return category; }
    public void setCategory(ExpenseCategory category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
