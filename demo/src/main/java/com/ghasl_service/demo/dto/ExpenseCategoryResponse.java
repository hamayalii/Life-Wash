package com.ghasl_service.demo.dto;

import com.ghasl_service.demo.model.ExpenseCategory;

public class ExpenseCategoryResponse {
    private String value;
    private String kurdishLabel;

    public ExpenseCategoryResponse(ExpenseCategory category) {
        this.value = category.getValue();
        this.kurdishLabel = category.getKurdishTranslation();
    }

    // Getters and Setters
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getKurdishLabel() { return kurdishLabel; }
    public void setKurdishLabel(String kurdishLabel) { this.kurdishLabel = kurdishLabel; }
}
