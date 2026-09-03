package com.ghasl_service.demo.service;

import com.ghasl_service.demo.dto.ActiveServiceDTO;
import com.ghasl_service.demo.dto.ServicePricingResponse;
import com.ghasl_service.demo.dto.ServicePricingUpdateRequest;
import com.ghasl_service.demo.model.Service;
import com.ghasl_service.demo.model.ServicePricing;
import com.ghasl_service.demo.repository.ServicePricingRepository;
import com.ghasl_service.demo.repository.ServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class ServicePricingService {
    
    private static final Logger logger = LoggerFactory.getLogger(ServicePricingService.class);
    
    private final ServicePricingRepository pricingRepository;
    private final ServiceRepository serviceRepository;
    
    public ServicePricingService(ServicePricingRepository pricingRepository, ServiceRepository serviceRepository) {
        this.pricingRepository = pricingRepository;
        this.serviceRepository = serviceRepository;
    }
    
    public List<ServicePricingResponse> getAllPricing() {
        return pricingRepository.findByServiceCategory_IsActiveTrueOrderByServiceCategory_EnglishNameAsc()
            .stream()
            .map(ServicePricingResponse::new)
            .collect(Collectors.toList());
    }
    
    public ServicePricingResponse getPricingByCategory(String categoryName) {
        ServicePricing pricing = pricingRepository.findByServiceCategory_EnglishName(categoryName.toUpperCase())
            .orElseThrow(() -> new IllegalArgumentException("Service pricing not found: " + categoryName));
        return new ServicePricingResponse(pricing);
    }
    
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "activeServices", allEntries = true)
    public ServicePricingResponse updatePricing(Long id, ServicePricingUpdateRequest request) {
        ServicePricing pricing = pricingRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Service pricing not found with ID: " + id));
        
        if (request.getBasePrice() != null) {
            pricing.setBasePrice(request.getBasePrice());
        }
        
        if (request.getDiscountPrice() != null) {
            pricing.setDiscountPrice(request.getDiscountPrice());
        }
        
        if (request.getDiscountPercentage() != null) {
            pricing.setDiscountPercentage(request.getDiscountPercentage());
        }
        
        if (request.getDiscountStartDate() != null) {
            pricing.setDiscountStartDate(request.getDiscountStartDate());
        }
        
        if (request.getDiscountEndDate() != null) {
            pricing.setDiscountEndDate(request.getDiscountEndDate());
        }
        
        if (request.getIsDiscountActive() != null) {
            pricing.setIsDiscountActive(request.getIsDiscountActive());
        }
        
        if (request.getSofaStandardSetSize() != null) {
            pricing.setSofaStandardSetSize(request.getSofaStandardSetSize());
        }
        
        ServicePricing saved = pricingRepository.save(pricing);
        return new ServicePricingResponse(saved);
    }
    
    @Transactional
    public void initializeDefaultPricing() {
        // This method is now obsolete - ServicePricingSeeder handles initialization
        // Kept for backward compatibility but does nothing
    }
    
    /**
     * Get all active services as flattened ActiveServiceDTO.
     * This is the Single Source of Truth for all client interfaces.
     * 
     * CRITICAL: Implements graceful degradation - skips corrupted records instead of crashing entire payload.
     * Logs WARN for each skipped record to aid debugging without blocking all clients.
     * 
     * Cached for 15 minutes to reduce database load.
     * 
     * @return List of ActiveServiceDTO with calculated effective prices (excluding corrupted records)
     */
    @org.springframework.cache.annotation.Cacheable(value = "activeServices", key = "'all'")
    public List<ActiveServiceDTO> getActiveServices() {
        // Fetch only active pricing records (soft delete filter)
        List<ServicePricing> allPricing = pricingRepository.findByServiceCategory_IsActiveTrueOrderByServiceCategory_EnglishNameAsc();
        
        // Batch fetch all Service entities by category IDs to avoid N+1
        List<Long> categoryIds = allPricing.stream()
            .map(p -> p.getServiceCategory().getId())
            .distinct()
            .toList();
        
        java.util.Map<Long, Service> serviceByCategoryId = serviceRepository.findAllByCategoryIdIn(categoryIds)
            .stream()
            .collect(java.util.stream.Collectors.toMap(s -> s.getCategory().getId(), java.util.function.Function.identity()));
        
        // Map with batch-fetched services
        return allPricing.stream()
            .map(pricing -> {
                try {
                    Service service = serviceByCategoryId.get(pricing.getServiceCategory().getId());
                    if (service == null) {
                        throw new IllegalStateException("Service not found for category ID: " + pricing.getServiceCategory().getId());
                    }
                    return Optional.ofNullable(mapToActiveServiceDTO(pricing, service));
                } catch (Exception e) {
                    logger.warn("Skipping corrupted service record (category ID: {}): {}", 
                        pricing.getServiceCategory() != null ? pricing.getServiceCategory().getId() : "null", 
                        e.getMessage());
                    return Optional.<ActiveServiceDTO>empty();
                }
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }
    
    /**
     * Map ServicePricing entity to ActiveServiceDTO with calculated effective price.
     * Backend performs discount calculation, not frontend.
     */
    private ActiveServiceDTO mapToActiveServiceDTO(ServicePricing pricing, Service coreService) {
        BigDecimal activePrice = pricing.getBasePrice();
        BigDecimal discountedPrice = null;
        BigDecimal discountPercentage = null;
        boolean discountActive = false;
        
        // Calculate effective price if discount is currently active
        if (pricing.isDiscountCurrentlyActive()) {
            discountedPrice = pricing.getDiscountPrice();
            discountPercentage = pricing.getDiscountPercentage();
            discountActive = true;
            // Use discounted price as active price
            activePrice = discountedPrice;
        }
        
        // Map icon based on service category (can be enhanced with iconUrl field in future)
        String iconUrl = mapIconToServiceCategory(pricing.getServiceCategory().getEnglishName());
        
        // Defensive null-safe boolean handling to prevent NPE from database NULL values
        boolean customPriced = Boolean.TRUE.equals(pricing.getIsCustomPriced());
        
        // Use the passed coreService parameter (batch-fetched to avoid N+1 queries)
        Long coreServiceId = coreService.getId();
        Long categoryId = pricing.getServiceCategory().getId();
        
        return new ActiveServiceDTO(
            coreServiceId,  // Use Service.id as primary ID (single source of truth)
            categoryId,  // ServiceCategory.id as secondary field (for reference)
            pricing.getServiceCategory().getEnglishName(),
            pricing.getServiceCategory().getKurdishName(),
            pricing.getPricingUnit(),
            activePrice,
            discountedPrice,
            discountPercentage,
            discountActive,
            customPriced,
            pricing.getSofaStandardSetSize(),
            iconUrl
        );
    }
    
    /**
     * Map service category to icon URL or FontAwesome class.
     * This can be enhanced to use actual icon URLs from database in future.
     */
    private String mapIconToServiceCategory(String englishName) {
        // Return FontAwesome class names for now - can be enhanced to actual URLs
        switch (englishName) {
            case "CARPET": return "fa-rug";
            case "RUG": return "fa-scroll";
            case "BLANKET": return "fa-bed";
            case "CURTAINS": return "fa-person-booth";
            case "SOFA": return "fa-couch";
            case "ROOF_TANK": return "fa-droplet";
            default: return "fa-circle";
        }
    }
}
