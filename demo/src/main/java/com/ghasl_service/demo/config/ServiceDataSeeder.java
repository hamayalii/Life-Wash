package com.ghasl_service.demo.config;

import com.ghasl_service.demo.model.MeasurementUnit;
import com.ghasl_service.demo.model.Service;
import com.ghasl_service.demo.model.ServiceCategory;
import com.ghasl_service.demo.repository.ServiceCategoryRepository;
import com.ghasl_service.demo.repository.ServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Database seeder for Service entities.
 * Ensures the database has the exact 7 services defined in the original system.
 * Each Service entity is linked to a ServiceCategory for proper POS order integration.
 * Only runs if the service table is empty (count == 0).
 * Runs SECOND (@Order(2)) after ServicePricingSeeder to ensure categories/pricing exist.
 */
@Component
@Order(2)
public class ServiceDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ServiceDataSeeder.class);

    private final ServiceRepository serviceRepository;
    private final ServiceCategoryRepository categoryRepository;

    public ServiceDataSeeder(ServiceRepository serviceRepository, 
                           ServiceCategoryRepository categoryRepository) {
        this.serviceRepository = serviceRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // First Principles: Only seed if database is empty
        if (serviceRepository.count() == 0) {
            log.info("Database is empty. Seeding initial services...");
            
            // Fetch ServiceCategory entities to establish proper relationships
            // Self-healing: Create missing categories instead of crashing
            ServiceCategory carpet = categoryRepository.findByEnglishName("CARPET")
                .orElseGet(() -> createAndSaveCategory("CARPET", "فەرش"));
            ServiceCategory rug = categoryRepository.findByEnglishName("RUG")
                .orElseGet(() -> createAndSaveCategory("RUG", "کومبار"));
            ServiceCategory blanket = categoryRepository.findByEnglishName("BLANKET")
                .orElseGet(() -> createAndSaveCategory("BLANKET", "بەتانی"));
            ServiceCategory curtains = categoryRepository.findByEnglishName("CURTAINS")
                .orElseGet(() -> createAndSaveCategory("CURTAINS", "پەردە"));
            ServiceCategory sofa = categoryRepository.findByEnglishName("SOFA")
                .orElseGet(() -> createAndSaveCategory("SOFA", "قەنەفە"));
            ServiceCategory roofTank = categoryRepository.findByEnglishName("ROOF_TANK")
                .orElseGet(() -> createAndSaveCategory("ROOF_TANK", "تەنکی سەربان"));
            ServiceCategory houseCleaning = categoryRepository.findByEnglishName("HOUSE_CLEANING")
                .orElseGet(() -> createAndSaveCategory("HOUSE_CLEANING", "پاککردنەوەی ماڵ/شوقە/باخ"));

            // Note: Pricing is handled by ServicePricingSeeder, not here

            // Create Service entities with proper category relationships
            // CRITICAL: Service.category relationship enables findByCategoryId query
            Service service1 = new Service();
            service1.setCategory(carpet);
            service1.setName("فەرش");
            service1.setBasePrice(new BigDecimal("1250"));
            service1.setDefaultUnitType(MeasurementUnit.PER_SQUARE_METER);
            
            Service service2 = new Service();
            service2.setCategory(rug);
            service2.setName("کومبار");
            service2.setBasePrice(new BigDecimal("1250"));
            service2.setDefaultUnitType(MeasurementUnit.PER_SQUARE_METER);
            
            Service service3 = new Service();
            service3.setCategory(blanket);
            service3.setName("بەتانی");
            service3.setBasePrice(new BigDecimal("5000"));
            service3.setDefaultUnitType(MeasurementUnit.PER_PIECE);
            
            Service service4 = new Service();
            service4.setCategory(curtains);
            service4.setName("پەردە");
            service4.setBasePrice(new BigDecimal("1500"));
            service4.setDefaultUnitType(MeasurementUnit.PER_SQUARE_METER);
            
            Service service5 = new Service();
            service5.setCategory(sofa);
            service5.setName("قەنەفە");
            service5.setBasePrice(new BigDecimal("40000"));
            service5.setDefaultUnitType(MeasurementUnit.PER_PERSON);
            
            Service service6 = new Service();
            service6.setCategory(roofTank);
            service6.setName("تەنکی سەربان");
            service6.setBasePrice(new BigDecimal("25000"));
            service6.setDefaultUnitType(MeasurementUnit.PER_PIECE);
            
            Service service7 = new Service();
            service7.setCategory(houseCleaning);
            service7.setName("پاککردنەوەی ماڵ/شوقە/باخ");
            service7.setBasePrice(null); // Negotiated price
            service7.setDefaultUnitType(MeasurementUnit.JOB);
            
            List<Service> services = Arrays.asList(service1, service2, service3, service4, service5, service6, service7);
            
            serviceRepository.saveAll(services);
            log.info("Successfully seeded {} services into database.", services.size());
            
            // Log each service for verification
            services.forEach(service -> {
                log.info("  - {} (ID: {}, Price: {} IQD, Unit: {})",
                    service.getName(),
                    service.getId(),
                    service.getBasePrice() != null ? service.getBasePrice() : "NEGOTIATED",
                    service.getDefaultUnitType()
                );
            });
            
        } else {
            log.info("Database already contains {} services. Skipping seeding.", serviceRepository.count());
        }
    }

    /**
     * Helper method to create and save a ServiceCategory if it doesn't exist.
     * Self-healing logic to prevent startup crashes.
     */
    private ServiceCategory createAndSaveCategory(String englishName, String kurdishName) {
        log.warn("Category '{}' not found. Creating it automatically.", englishName);
        ServiceCategory category = new ServiceCategory();
        category.setEnglishName(englishName);
        category.setKurdishName(kurdishName);
        return categoryRepository.save(category);
    }
}
