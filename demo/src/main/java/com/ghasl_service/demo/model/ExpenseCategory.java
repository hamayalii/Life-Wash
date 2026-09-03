package com.ghasl_service.demo.model;

/**
 * Enum representing operational expense categories.
 * Serves as Single Source of Truth for expense categorization.
 */
public enum ExpenseCategory {
    WAGES("کرێی شاگرد"),
    RENT("کرێی دوکان"),
    DETERGENTS("شامپۆ و پاککەرەوە"),
    CLEANING_EQUIPMENT("ئامێری پاککەرەوە"),
    FUEL("بەنزین"),
    CAR_MAINTENANCE("عەتەڵاتی سەیارە"),
    UTILITIES("کارەبا، ئاو و ئینتەرنێت"),
    FOOD_HOSPITALITY("خواردن و میوانداری"),
    TAXES_FEES("باج و ڕسومات"),
    OTHER("هی تر");

    private final String kurdishTranslation;

    ExpenseCategory(String kurdishTranslation) {
        this.kurdishTranslation = kurdishTranslation;
    }

    public String getKurdishTranslation() {
        return kurdishTranslation;
    }

    public String getValue() {
        return this.name();
    }
}
