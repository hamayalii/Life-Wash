package com.ghasl_service.demo.repository;

import com.ghasl_service.demo.model.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<Service, Long> {
    Optional<Service> findByCategoryId(Long categoryId);
    
    List<Service> findAllByCategoryIdIn(List<Long> categoryIds);
}
