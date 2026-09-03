package com.ghasl_service.demo.config;

import com.ghasl_service.demo.model.MeasurementUnit;
import com.ghasl_service.demo.model.ServiceCategory;
import com.ghasl_service.demo.model.ServicePricing;
import com.ghasl_service.demo.repository.ServiceCategoryRepository;
import com.ghasl_service.demo.repository.ServicePricingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/*
 * Database seeder for ServiceCategory and ServicePricing entities.
 * Ensures the service_categories and service_pricing tables have default data.
 * Only runs if the service_categories table is empty (count == 0).
 * This provides self-healing initialization for the pricing system.
 * Runs FIRST (@Order(1)) to ensure categories/pricing exist before services.
 */
@Component
@Order(1)
public class ServicePricingSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ServicePricingSeeder.class);

    private final ServiceCategoryRepository categoryRepository;
    private final ServicePricingRepository pricingRepository;

    public ServicePricingSeeder(ServiceCategoryRepository categoryRepository, 
                               ServicePricingRepository pricingRepository) {
        this.categoryRepository = categoryRepository;
        this.pricingRepository = pricingRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Only seed if categories table is empty
        if (categoryRepository.count() == 0) {
            log.info("Service categories table is empty. Seeding default categories...");
            
            // Create default service categories
            ServiceCategory carpet = createServiceCategory("CARPET", "فەرش");
            categoryRepository.save(carpet);
            
            ServiceCategory rug = createServiceCategory("RUG", "کومبار");
            categoryRepository.save(rug);
            
            ServiceCategory blanket = createServiceCategory("BLANKET", "بەتانی");
            categoryRepository.save(blanket);
            
            ServiceCategory curtains = createServiceCategory("CURTAINS", "پەردە");
            categoryRepository.save(curtains);
            
            ServiceCategory sofa = createServiceCategory("SOFA", "قەنەفە");
            categoryRepository.save(sofa);
            
            ServiceCategory roofTank = createServiceCategory("ROOF_TANK", "تەنکی سەربان");
            categoryRepository.save(roofTank);
            
            ServiceCategory houseCleaning = createServiceCategory("HOUSE_CLEANING", "پاککردنەوەی ماڵ/شوقە/باخ");
            categoryRepository.save(houseCleaning);
            
            log.info("Successfully seeded {} service categories into database.", categoryRepository.count());
            
            // Now seed pricing data
            seedPricingData(carpet, rug, blanket, curtains, sofa, roofTank, houseCleaning);
            
        } else {
            log.info("Service categories table already contains {} records. Skipping seeding.", categoryRepository.count());
        }
        
        // FORCE STATE CORRECTION: Fix existing corrupted data
        // This runs regardless of whether seeding occurred
        forceCorrectCategoryStates();
        
        // FORCE UPDATE: Ensure HOUSE_CLEANING has isCustomPriced=true and basePrice=0.00
        forceUpdateHouseCleaningCustomPricing();
    }
    
    /**
     * Force correction of category states to fix corrupted data from previous runs.
     * Ensures all categories are active and have corresponding pricing records.
     */
    @Transactional
    private void forceCorrectCategoryStates() {
        log.info("Starting forced state correction for existing categories...");
        
        // Fetch all categories
        List<ServiceCategory> allCategories = categoryRepository.findAll();
        boolean stateChanged = false;
        
        for (ServiceCategory category : allCategories) {
            // Fix isActive flag
            if (category.getIsActive() == null || !category.getIsActive()) {
                category.setIsActive(true);
                stateChanged = true;
                log.warn("State Correction: Set isActive=true for category: {}", category.getEnglishName());
            }
            
            // Ensure ServicePricing exists for this category
            pricingRepository.findByServiceCategory(category)
                .orElseGet(() -> {
                    log.warn("State Correction: Creating missing pricing for category: {}", category.getEnglishName());
                    ServicePricing pricing = createDefaultPricingForCategory(category);
                    return pricingRepository.save(pricing);
                });
        }
        
        if (stateChanged) {
            categoryRepository.saveAll(allCategories);
            log.info("State Correction: Updated {} categories to active state.", allCategories.size());
        } else {
            log.info("State Correction: All categories already in correct active state.");
        }
    }
    
    /**
     * Create default pricing for a category based on its english name.
     */
    private ServicePricing createDefaultPricingForCategory(ServiceCategory category) {
        ServicePricing pricing = new ServicePricing();
        pricing.setServiceCategory(category);
        
        // Set default pricing based on category type
        switch (category.getEnglishName()) {
            case "CARPET":
            case "RUG":
                pricing.setPricingUnit(MeasurementUnit.PER_METER);
                pricing.setBasePrice(new BigDecimal("1250.00"));
                break;
            case "BLANKET":
                pricing.setPricingUnit(MeasurementUnit.PER_PIECE);
                pricing.setBasePrice(new BigDecimal("5000.00"));
                break;
            case "CURTAINS":
                pricing.setPricingUnit(MeasurementUnit.PER_METER);
                pricing.setBasePrice(new BigDecimal("1500.00"));
                break;
            case "SOFA":
                pricing.setPricingUnit(MeasurementUnit.PER_PERSON);
                pricing.setBasePrice(new BigDecimal("40000.00"));
                pricing.setSofaStandardSetSize(10);
                break;
            case "ROOF_TANK":
                pricing.setPricingUnit(MeasurementUnit.PER_PIECE);
                pricing.setBasePrice(new BigDecimal("15000.00"));
                break;
            case "HOUSE_CLEANING":
                pricing.setPricingUnit(MeasurementUnit.JOB);
                pricing.setBasePrice(new BigDecimal("0.00"));
                pricing.setIsCustomPriced(true);
                break;
            default:
                // Fallback for unknown categories
                pricing.setPricingUnit(MeasurementUnit.PER_PIECE);
                pricing.setBasePrice(new BigDecimal("1000.00"));
        }
        
        return pricing;
    }
    
    private void seedPricingData(ServiceCategory carpet, ServiceCategory rug, ServiceCategory blanket, 
                                 ServiceCategory curtains, ServiceCategory sofa, ServiceCategory roofTank,
                                 ServiceCategory houseCleaning) {
        if (pricingRepository.count() == 0) {
            log.info("Service pricing table is empty. Seeding default pricing data...");
            
            // Carpet (فەرش) - 1250 IQD per meter
            ServicePricing carpetPricing = createServicePricing(
                carpet,
                MeasurementUnit.PER_METER,
                new BigDecimal("1250.00")
            );
            pricingRepository.save(carpetPricing);
            log.info("  - Seeded CARPET (فەرش): 1250.00 IQD/meter");
            
            // Rug (کومبار) - 1250 IQD per meter
            ServicePricing rugPricing = createServicePricing(
                rug,
                MeasurementUnit.PER_METER,
                new BigDecimal("1250.00")
            );
            pricingRepository.save(rugPricing);
            log.info("  - Seeded RUG (کومبار): 1250.00 IQD/meter");
            
            // Blanket (بەتانی) - 5000 IQD per piece
            ServicePricing blanketPricing = createServicePricing(
                blanket,
                MeasurementUnit.PER_PIECE,
                new BigDecimal("5000.00")
            );
            pricingRepository.save(blanketPricing);
            log.info("  - Seeded BLANKET (بەتانی): 5000.00 IQD/piece");
            
            // Curtains (پەردە) - 1500 IQD per meter
            ServicePricing curtainsPricing = createServicePricing(
                curtains,
                MeasurementUnit.PER_METER,
                new BigDecimal("1500.00")
            );
            pricingRepository.save(curtainsPricing);
            log.info("  - Seeded CURTAINS (پەردە): 1500.00 IQD/meter");
            
            // Sofa (قەنەفە) - 40000 IQD total, 10 seats standard
            ServicePricing sofaPricing = new ServicePricing();
            sofaPricing.setServiceCategory(sofa);
            sofaPricing.setPricingUnit(MeasurementUnit.PER_PERSON);
            sofaPricing.setBasePrice(new BigDecimal("40000.00"));
            sofaPricing.setSofaStandardSetSize(10);
            pricingRepository.save(sofaPricing);
            log.info("  - Seeded SOFA (قەنەفە): 40000.00 IQD total, 10 seats standard");
            
            // Roof Tank (تەنکی سەربان) - 15000 IQD per piece
            ServicePricing roofTankPricing = createServicePricing(
                roofTank,
                MeasurementUnit.PER_PIECE,
                new BigDecimal("15000.00")
            );
            pricingRepository.save(roofTankPricing);
            log.info("  - Seeded ROOF_TANK (تەنکی سەربان): 15000.00 IQD/piece");
            
            // House Cleaning (پاککردنەوەی ماڵ/شوقە/باخ) - Custom priced (on-site inspection)
            ServicePricing houseCleaningPricing = new ServicePricing();
            houseCleaningPricing.setServiceCategory(houseCleaning);
            houseCleaningPricing.setPricingUnit(MeasurementUnit.PER_PIECE);
            houseCleaningPricing.setBasePrice(new BigDecimal("0.00"));
            houseCleaningPricing.setIsCustomPriced(true);
            pricingRepository.save(houseCleaningPricing);
            log.info("  - Seeded HOUSE_CLEANING (پاککردنەوەی ماڵ/شوقە/باخ): Custom priced (on-site inspection)");
            
            log.info("Successfully seeded {} service pricing records into database.", pricingRepository.count());
            
        } else {
            log.info("Service pricing table already contains {} records. Skipping seeding.", pricingRepository.count());
        }
    }
    
    /**
     * Helper method to create a ServiceCategory entity.
     */
    private ServiceCategory createServiceCategory(String englishName, String kurdishName) {
        ServiceCategory category = new ServiceCategory();
        category.setEnglishName(englishName);
        category.setKurdishName(kurdishName);
        category.setIsActive(true); // CRITICAL: Set active for API visibility
        return category;
    }
    
    /**
     * Helper method to create a basic ServicePricing entity.
     */
    private ServicePricing createServicePricing(ServiceCategory category, 
                                               MeasurementUnit pricingUnit,
                                               BigDecimal basePrice) {
        ServicePricing pricing = new ServicePricing();
        pricing.setServiceCategory(category);
        pricing.setPricingUnit(pricingUnit);
        pricing.setBasePrice(basePrice);
        // Discount fields default to null/false via entity defaults
        return pricing;
    }
    
    /**
     * Force update HOUSE_CLEANING service to have custom pricing enabled.
     * This fixes stale data where existing records don't have the isCustomPriced flag set.
     */
    @Transactional
    private void forceUpdateHouseCleaningCustomPricing() {
        categoryRepository.findByEnglishName("HOUSE_CLEANING").ifPresent(category -> {
            pricingRepository.findByServiceCategory(category).ifPresent(pricing -> {
                if (!Boolean.TRUE.equals(pricing.getIsCustomPriced())) {
                    pricing.setIsCustomPriced(true);
                    pricing.setBasePrice(BigDecimal.ZERO);
                    pricingRepository.save(pricing);
                    log.info("System Override: Forced HOUSE_CLEANING to variable pricing.");
                }
            });
        });
    }
}
