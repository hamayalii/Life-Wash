package com.ghasl_service.demo.repository;

import com.ghasl_service.demo.model.ServiceCategory;
import com.ghasl_service.demo.model.ServicePricing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServicePricingRepository extends JpaRepository<ServicePricing, Long> {
    
    Optional<ServicePricing> findByServiceCategory_EnglishName(String englishName);
    
    Optional<ServicePricing> findByServiceCategoryEnglishName(String englishName);
    
    Optional<ServicePricing> findByServiceCategory(ServiceCategory category);
    
    List<ServicePricing> findAllByOrderByServiceCategory_EnglishNameAsc();
    
    List<ServicePricing> findByServiceCategory_IsActiveTrueOrderByServiceCategory_EnglishNameAsc();
    
    boolean existsByServiceCategory_EnglishName(String englishName);
}
