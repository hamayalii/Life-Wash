package com.ghasl_service.demo.repository;

import com.ghasl_service.demo.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEvent.EventStatus status);
    
    List<OutboxEvent> findByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
        OutboxEvent.EventStatus status, 
        Integer maxRetries
    );
    
    List<OutboxEvent> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
        OutboxEvent.EventStatus status,
        LocalDateTime beforeTime
    );
}
