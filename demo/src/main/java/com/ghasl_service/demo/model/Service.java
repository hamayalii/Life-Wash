package com.ghasl_service.demo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "services")
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private ServiceCategory category;

    private String name;

    /**
     * Base price for the service. Null for services requiring manual price negotiation
     * (e.g., Sofa cleaning, House cleaning where price depends on condition/size).
     */
    private BigDecimal basePrice;

    /**
     * Default unit type for this service using the global MeasurementUnit enum.
     * This ensures type safety across the entire system.
     */
    @Enumerated(EnumType.STRING)
    private MeasurementUnit defaultUnitType;

    public Service() {
    }

    public Service(String name, BigDecimal basePrice, MeasurementUnit defaultUnitType) {
        this.name = name;
        this.basePrice = basePrice;
        this.defaultUnitType = defaultUnitType;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ServiceCategory getCategory() {
        return category;
    }

    public void setCategory(ServiceCategory category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public MeasurementUnit getDefaultUnitType() {
        return defaultUnitType;
    }

    public void setDefaultUnitType(MeasurementUnit defaultUnitType) {
        this.defaultUnitType = defaultUnitType;
    }
}
