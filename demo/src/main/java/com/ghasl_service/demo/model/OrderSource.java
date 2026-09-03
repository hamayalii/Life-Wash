package com.ghasl_service.demo.model;

/**
 * Enum representing the source/channel where an order originated.
 * Replaces fragile magic strings in the createdBy field.
 * Serves as Single Source of Truth for order source tracking.
 */
public enum OrderSource {
    WEB("Web Order Form"),
    POS("Point of Sale"),
    TELEGRAM_BOT("Telegram Bot");

    private final String description;

    OrderSource(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return this.name();
    }
}
