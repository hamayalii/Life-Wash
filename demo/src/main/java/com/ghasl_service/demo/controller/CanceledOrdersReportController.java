package com.ghasl_service.demo.controller;

import com.ghasl_service.demo.dto.CanceledReasonsResponse;
import com.ghasl_service.demo.model.RejectionReason;
import com.ghasl_service.demo.repository.OrderRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller for canceled orders analysis reports.
 * Provides endpoint for the horizontal bar chart showing rejection reasons.
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
public class CanceledOrdersReportController {
    
    private final OrderRepository orderRepository;
    
    public CanceledOrdersReportController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }
    
    /**
     * GET /api/v1/admin/reports/canceled-reasons
     * Returns rejected orders grouped by rejection reason, ordered by count descending.
     * Used by the canceled orders analysis horizontal bar chart.
     */
    @GetMapping("/canceled-reasons")
    public List<CanceledReasonsResponse> getCanceledReasons() {
        List<Object[]> results = orderRepository.countRejectedOrdersByReason();
        
        return results.stream()
            .map(row -> {
                RejectionReason reason = (RejectionReason) row[0];
                Long count = (Long) row[1];
                return new CanceledReasonsResponse(
                    reason.getValue(),
                    reason.getKurdishTranslation(),
                    count
                );
            })
            .collect(Collectors.toList());
    }
}
