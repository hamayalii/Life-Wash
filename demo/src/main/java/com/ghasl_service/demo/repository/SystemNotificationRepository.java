package com.ghasl_service.demo.repository;

import com.ghasl_service.demo.model.SystemNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemNotificationRepository extends JpaRepository<SystemNotification, Long> {

    long countByIsReadFalse();

    List<SystemNotification> findAllByOrderByCreatedAtDesc();
}
