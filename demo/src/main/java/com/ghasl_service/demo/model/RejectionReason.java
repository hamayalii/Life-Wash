package com.ghasl_service.demo.model;

/**
 * Enum representing the reasons why an order might be rejected.
 * Serves as Single Source of Truth for rejection reasons across all interfaces.
 * Used by Dashboard, POS, and Telegram Bot.
 */
public enum RejectionReason {
    PRICE_TOO_HIGH("موشتەری نرخی پێ گران بوو"),
    NO_RESPONSE("کڕیار وەڵامی نەدایەوە"),
    NO_TIME("کاتمان نەبوو"),
    LOCATION_TOO_FAR("شوێنی کڕیار دوور بوو"),
    OTHER("هۆکاری تر");

    private final String kurdishTranslation;

    RejectionReason(String kurdishTranslation) {
        this.kurdishTranslation = kurdishTranslation;
    }

    public String getKurdishTranslation() {
        return kurdishTranslation;
    }

    public String getValue() {
        return this.name();
    }
}
