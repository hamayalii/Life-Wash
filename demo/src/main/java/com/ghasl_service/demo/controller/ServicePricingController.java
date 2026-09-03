package com.ghasl_service.demo.controller;

import com.ghasl_service.demo.dto.ServicePricingResponse;
import com.ghasl_service.demo.dto.ServicePricingUpdateRequest;
import com.ghasl_service.demo.service.ServicePricingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/services/pricing")
public class ServicePricingController {
    
    private final ServicePricingService pricingService;
    
    public ServicePricingController(ServicePricingService pricingService) {
        this.pricingService = pricingService;
    }
    
    @GetMapping("/all")
    public ResponseEntity<List<ServicePricingResponse>> getAllPricing() {
        return ResponseEntity.ok(pricingService.getAllPricing());
    }
    
    @GetMapping("/category/{categoryName}")
    public ResponseEntity<ServicePricingResponse> getPricingByCategory(@PathVariable String categoryName) {
        try {
            return ResponseEntity.ok(pricingService.getPricingByCategory(categoryName));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<ServicePricingResponse> updatePricing(
            @PathVariable Long id,
            @RequestBody ServicePricingUpdateRequest request) {
        try {
            ServicePricingResponse updated = pricingService.updatePricing(id, request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PostMapping("/initialize")
    public ResponseEntity<String> initializeDefaultPricing() {
        pricingService.initializeDefaultPricing();
        return ResponseEntity.ok("Default pricing initialized successfully");
    }
}
