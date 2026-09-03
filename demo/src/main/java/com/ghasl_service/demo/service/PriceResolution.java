package com.ghasl_service.demo.service;

import java.math.BigDecimal;

/**
 * Sealed type representing the three possible outcomes of a pricing look-up.
 *
 * <ul>
 *   <li>{@link Computed} — a definitive price (unit × quantity) was calculated.
 *       Create an Order and charge this amount.</li>
 *   <li>{@link PendingAdmin} — the service type was recognised and an Order
 *       <em>should</em> be created (so Telegram fires), but the final price
 *       must be confirmed by the admin over the phone before collection.
 *       price = null in the persisted Order.</li>
 *   <li>{@link NotApplicable} — no Order is meaningful yet (e.g. "antique" /
 *       home-cleaning inquiry). Save the lead only and flag for manual follow-up.</li>
 * </ul>
 */
public sealed interface PriceResolution
        permits PriceResolution.Computed,
                PriceResolution.PendingAdmin,
                PriceResolution.NotApplicable {

    /** A definitive, computable price: unit_price × quantity. */
    record Computed(BigDecimal amount) implements PriceResolution {}

    /**
     * Price is not deterministic from the form data alone; admin must confirm
     * by phone. An Order is still created so Telegram notifies the admin.
     *
     * @param reason human-readable explanation for log / admin UI
     */
    record PendingAdmin(String reason) implements PriceResolution {}

    /**
     * The service type does not map to any standard pricing model (e.g.
     * "antique" home/shop/garden cleaning). Only a lead is saved.
     */
    record NotApplicable() implements PriceResolution {}
}
