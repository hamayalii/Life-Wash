package com.ghasl_service.demo.controller;

import com.ghasl_service.demo.dto.ActiveServiceDTO;
import com.ghasl_service.demo.dto.NewServiceRequest;
import com.ghasl_service.demo.model.Service;
import com.ghasl_service.demo.model.ServiceCategory;
import com.ghasl_service.demo.model.ServicePricing;
import com.ghasl_service.demo.repository.ServiceCategoryRepository;
import com.ghasl_service.demo.repository.ServicePricingRepository;
import com.ghasl_service.demo.repository.ServiceRepository;
import com.ghasl_service.demo.service.ServicePricingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * REST controller for service management.
 * Provides endpoints for the POS to fetch available services dynamically.
 */
@RestController
@RequestMapping("/api/v1/services")
public class ServiceController {

    private final ServiceRepository serviceRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final ServicePricingRepository pricingRepository;
    private final ServicePricingService pricingService;

    public ServiceController(ServiceRepository serviceRepository,
                          ServiceCategoryRepository categoryRepository,
                          ServicePricingRepository pricingRepository,
                          ServicePricingService pricingService) {
        this.serviceRepository = serviceRepository;
        this.categoryRepository = categoryRepository;
        this.pricingRepository = pricingRepository;
        this.pricingService = pricingService;
    }

    /**
     * GET /api/v1/services
     * Returns all available services for the POS system.
     * This enables Single Source of Truth - frontend fetches real database IDs.
     */
    @GetMapping
    public List<Service> getAllServices() {
        return serviceRepository.findAll();
    }
    
    /**
     * GET /api/v1/services/active
     * Returns all active services as flattened ActiveServiceDTO.
     * This is the Single Source of Truth for all client interfaces:
     * - Customer Order Form (Homepage)
     * - POS Module
     * - Admin Dashboard
     * - Telegram Bot (for dynamic service filtering)
     * 
     * Backend performs discount calculation, not frontend.
     */
    @GetMapping("/active")
    public List<ActiveServiceDTO> getActiveServices() {
        return pricingService.getActiveServices();
    }
    
    /**
     * POST /api/v1/services/new
     * Creates a new service category with default pricing.
     * This enables dynamic service creation from the settings UI.
     * 
     * CRITICAL: @Transactional ensures ACID compliance - if Service entity creation fails,
     * the entire transaction rolls back to prevent orphaned ServiceCategory/ServicePricing records.
     */
    @Transactional
    @PostMapping("/new")
    public ResponseEntity<?> createNewService(@RequestBody NewServiceRequest request) {
        try {
            // Validate request
            if (request.getKurdishName() == null || request.getKurdishName().trim().isEmpty() ||
                request.getEnglishName() == null || request.getEnglishName().trim().isEmpty() ||
                request.getBasePrice() == null || request.getPricingUnit() == null) {
                return ResponseEntity.badRequest().body("Missing required fields");
            }
            
            // Check if category already exists
            if (categoryRepository.existsByEnglishName(request.getEnglishName().trim().toUpperCase())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Service category with this English name already exists");
            }
            
            // Create new service category
            ServiceCategory category = new ServiceCategory();
            category.setEnglishName(request.getEnglishName().trim().toUpperCase());
            category.setKurdishName(request.getKurdishName().trim());
            category.setIsActive(true);
            category = categoryRepository.save(category);
            
            // Create Service entity linked to category (CRITICAL for referential integrity)
            Service service = new Service();
            service.setCategory(category);
            service = serviceRepository.save(service);
            
            // Create default pricing for the new service
            ServicePricing pricing = new ServicePricing();
            pricing.setServiceCategory(category);
            pricing.setPricingUnit(com.ghasl_service.demo.model.MeasurementUnit.valueOf(request.getPricingUnit().toUpperCase()));
            pricing.setBasePrice(request.getBasePrice());
            
            // Set sofa standard size if applicable
            if ("PER_PERSON".equals(request.getPricingUnit().toUpperCase()) && 
                request.getSofaStandardSetSize() != null) {
                pricing.setSofaStandardSetSize(request.getSofaStandardSetSize());
            }
            
            // Set custom pricing flag if provided
            if (request.getIsCustomPriced() != null) {
                pricing.setIsCustomPriced(request.getIsCustomPriced());
            }
            
            pricingRepository.save(pricing);
            
            return ResponseEntity.ok().body("Service created successfully");
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid pricing unit: " + request.getPricingUnit());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error creating service: " + e.getMessage());
        }
    }
    
    /**
     * DELETE /api/v1/services/{id}
     * Soft delete a service by setting isActive = false.
     * This prevents referential integrity violations with existing orders.
     * 
     * @param id The ServiceCategory ID to soft delete
     * @return ResponseEntity with success/error message
     */
    @Transactional
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteService(@PathVariable Long id) {
        try {
            ServiceCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Service not found with ID: " + id));
            
            // Soft delete: set isActive to false instead of hard delete
            category.setIsActive(false);
            categoryRepository.save(category);
            
            return ResponseEntity.ok().body("Service soft deleted successfully");
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error deleting service: " + e.getMessage());
        }
    }
}
