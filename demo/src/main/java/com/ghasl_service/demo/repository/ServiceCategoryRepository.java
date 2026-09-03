package com.ghasl_service.demo.repository;

import com.ghasl_service.demo.model.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {
    
    Optional<ServiceCategory> findByEnglishName(String englishName);
    
    boolean existsByEnglishName(String englishName);
    
    List<ServiceCategory> findByIsActiveTrueOrderByEnglishNameAsc();
}
