package com.ghasl_service.demo.model;

/**
 * Global enum for measurement units across the entire system.
 * This is the Single Source of Truth for unit types used in:
 * - Service entity (baseline unit type)
 * - ServicePricing entity (pricing unit)
 * - All DTOs and API responses
 * 
 * Replaces primitive String usage to ensure type safety.
 */
public enum MeasurementUnit {
    PER_METER,
    PER_PIECE,
    PER_PERSON,
    COUNT,
    HOURLY,
    PER_SQUARE_METER,
    PER_KILOGRAM,
    PER_LITER,
    JOB
}
