package com.ghasl_service.demo.service;

import com.ghasl_service.demo.model.ServiceCategory;
import com.ghasl_service.demo.model.ServicePricing;
import com.ghasl_service.demo.repository.ServiceCategoryRepository;
import com.ghasl_service.demo.repository.ServicePricingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Single-responsibility service: maps a rugType + quantity to a
 * {@link PriceResolution} — one of three explicit outcomes.
 *
 * <h3>Pricing table (index.html v2 — quantity-aware)</h3>
 * <pre>
 *   persian   → 1 250 IQD/metre  × quantity  → Computed
 *   wool      → admin confirms price by phone  → PendingAdmin
 *   silk      → 5 000 IQD/piece  × quantity  → Computed
 *   shag      → 1 500 IQD/metre  × quantity  → Computed
 *   synthetic → 25 000 IQD/piece × quantity  → Computed
 *   antique   → no fixed price (inquiry only) → NotApplicable
 * </pre>
 *
 * <h3>Quantity rules</h3>
 * <ul>
 *   <li>persian, shag — decimal quantity allowed (e.g. 3.5 m²)</li>
 *   <li>silk, synthetic — integer-only; fractional quantity → {@link IllegalArgumentException}</li>
 *   <li>wool — integer seat count; price determined by admin (PendingAdmin)</li>
 *   <li>antique — quantity ignored; always NotApplicable</li>
 *   <li>All computable types — quantity must be &gt; 0</li>
 * </ul>
 *
 * <h3>Unknown rugType</h3>
 * Throws {@link IllegalArgumentException} (maps to HTTP 400 in the controller).
 * This is a genuine client error, not a business pricing outcome.
 */
@Service
public class PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);

    private final ServiceCategoryRepository serviceCategoryRepository;
    private final ServicePricingRepository servicePricingRepository;

    public PricingService(ServiceCategoryRepository serviceCategoryRepository,
                         ServicePricingRepository servicePricingRepository) {
        this.serviceCategoryRepository = serviceCategoryRepository;
        this.servicePricingRepository = servicePricingRepository;
    }

    /**
     * Resolves the price for the given rugType and quantity.
     *
     * @param rugType  normalised or raw value from the form select
     * @param quantity number of units; may be null for antique (ignored)
     * @return one of {@link PriceResolution.Computed}, {@link PriceResolution.PendingAdmin},
     *         or {@link PriceResolution.NotApplicable}
     * @throws IllegalArgumentException if rugType is unrecognised, or if quantity
     *         is missing/invalid for a type that requires it
     */
    public PriceResolution resolve(String rugType, Double quantity) {
        if (rugType == null || rugType.isBlank()) {
            throw new IllegalArgumentException("rugType must not be null or blank");
        }

        String type = rugType.trim();

        // Special case: antique - no fixed price
        if (type.equalsIgnoreCase("antique")) {
            log.info("rugType='antique' → NotApplicable (home/shop/garden inquiry, no fixed price)");
            return new PriceResolution.NotApplicable();
        }

        // Special case: wool/sofa - requires admin confirmation
        if (type.equalsIgnoreCase("wool")) {
            if (quantity != null) {
                if (quantity <= 0) {
                    throw new IllegalArgumentException("quantity must be > 0 for rugType='wool'; got: " + quantity);
                }
                if (quantity - Math.floor(quantity) > 1e-9) {
                    throw new IllegalArgumentException("rugType='wool' requires an integer quantity; got: " + quantity);
                }
            }
            log.info("rugType='wool' quantity={} → PendingAdmin (admin confirms price by phone)", quantity);
            return new PriceResolution.PendingAdmin(
                    "Sofa pricing depends on fabric/soiling; admin confirms by phone. " +
                    "Seat count submitted: " + (quantity != null ? quantity.intValue() : "not provided"));
        }

        // Validate quantity for all other services
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0 for rugType='" + type + "'; got: " + quantity);
        }

        // PURE DYNAMIC DATABASE LOOKUP (NO HARDCODING - works for ANY service including "بەتانی")
        String normalizedInput = type.toUpperCase();
        
        // First try exact match on englishName (uppercase)
        ServiceCategory category = serviceCategoryRepository.findByEnglishName(normalizedInput)
            .orElse(null);
        
        // If not found, try case-insensitive kurdish name match
        if (category == null) {
            java.util.List<ServiceCategory> allCategories = serviceCategoryRepository.findAll();
            category = allCategories.stream()
                .filter(sc -> sc.getKurdishName() != null && 
                            sc.getKurdishName().equalsIgnoreCase(type))
                .findFirst()
                .orElse(null);
        }
        
        // Final reference for lambda usage (effectively final)
        final ServiceCategory resolvedCategory = category;
        
        if (resolvedCategory == null) {
            throw new IllegalArgumentException("Service category not found for rugType: " + type);
        }
        
        ServicePricing pricing = servicePricingRepository.findByServiceCategory(resolvedCategory)
            .orElseThrow(() -> new IllegalArgumentException("Service pricing not found for category: " + resolvedCategory.getEnglishName()));
        
        if (pricing.getBasePrice() == null) {
            throw new IllegalArgumentException("Base price not configured for category: " + resolvedCategory.getEnglishName());
        }
        
        BigDecimal basePrice = pricing.getBasePrice();
        BigDecimal effectivePrice = basePrice;
        String discountInfo = "none";
        
        // UNIVERSAL DISCOUNT MATHEMATICS (applies to ANY service)
        // Check if discount is active and within validity period
        boolean isDiscountActive = pricing.getIsDiscountActive() != null && pricing.getIsDiscountActive();
        boolean isWithinDiscountPeriod = false;
        
        if (isDiscountActive && pricing.getDiscountStartDate() != null && pricing.getDiscountEndDate() != null) {
            LocalDateTime now = LocalDateTime.now();
            isWithinDiscountPeriod = !now.isBefore(pricing.getDiscountStartDate()) && 
                                     !now.isAfter(pricing.getDiscountEndDate());
        }
        
        // Apply discount if active and within period
        if (isDiscountActive && isWithinDiscountPeriod) {
            if (pricing.getDiscountPrice() != null && pricing.getDiscountPrice().compareTo(BigDecimal.ZERO) > 0) {
                // Use explicit discount price
                effectivePrice = pricing.getDiscountPrice();
                discountInfo = String.format("discountPrice=%s (valid: %s to %s)",
                    effectivePrice, pricing.getDiscountStartDate(), pricing.getDiscountEndDate());
            } else if (pricing.getDiscountPercentage() != null && pricing.getDiscountPercentage().compareTo(BigDecimal.ZERO) > 0) {
                // Calculate discount from percentage
                BigDecimal discountAmount = basePrice.multiply(pricing.getDiscountPercentage())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                effectivePrice = basePrice.subtract(discountAmount);
                discountInfo = String.format("discountPercentage=%s%% (valid: %s to %s)",
                    pricing.getDiscountPercentage(), pricing.getDiscountStartDate(), pricing.getDiscountEndDate());
            }
        }
        
        BigDecimal total = effectivePrice.multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
        
        // ERROR level trace logging (as requested by user)
        log.error("TRACE - PricingService: Input='{}', FoundBasePrice={}, EffectiveDiscountedPrice={}, FinalTotal={}",
            type, basePrice, effectivePrice, total);
        
        return new PriceResolution.Computed(total);
    }
}
