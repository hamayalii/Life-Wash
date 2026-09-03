package com.ghasl_service.demo.service;

/**
 * Thrown by {@link PricingService} when the requested rug type has no
 * deterministic unit price — either because it requires a custom quote
 * (e.g. "antique") or because the rugType value is not recognised.
 *
 * <p>Callers <em>must</em> handle this explicitly: the business rule is to
 * save the lead and flag it for manual follow-up rather than defaulting to
 * zero or any fabricated price.
 */
public class PricingUnavailableException extends Exception {

    private final String rugType;

    public PricingUnavailableException(String rugType, String message) {
        super(message);
        this.rugType = rugType;
    }

    /** The rugType value that could not be priced. */
    public String getRugType() {
        return rugType;
    }
}
