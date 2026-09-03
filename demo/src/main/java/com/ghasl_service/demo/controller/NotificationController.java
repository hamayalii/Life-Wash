package com.ghasl_service.demo.controller;

import com.ghasl_service.demo.model.SystemNotification;
import com.ghasl_service.demo.repository.SystemNotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final SystemNotificationRepository notificationRepository;

    public NotificationController(SystemNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public ResponseEntity<List<SystemNotification>> getNotifications() {
        List<SystemNotification> notifications = notificationRepository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        long unreadCount = notificationRepository.countByIsReadFalse();
        Map<String, Long> response = new HashMap<>();
        response.put("count", unreadCount);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        return notificationRepository.findById(id)
                .map(notification -> {
                    notification.setIsRead(true);
                    notificationRepository.save(notification);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
