package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.InAppNotificationResponse;
import com.rinoimob.domain.dto.NotificationStatsResponse;
import com.rinoimob.service.notification.InAppNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications/in-app")
@RequiredArgsConstructor
public class InAppNotificationController {

    private final InAppNotificationService inAppNotificationService;

    /**
     * Get paginated notifications for the current user.
     */
    @GetMapping
    public ResponseEntity<Page<InAppNotificationResponse>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID userId = (UUID) request.getAttribute("userId");

        Page<InAppNotificationResponse> notifications = inAppNotificationService.getUserNotifications(
                tenantId, userId, page, size
        );

        return ResponseEntity.ok(notifications);
    }

    /**
     * Get unread notifications for the current user.
     */
    @GetMapping("/unread")
    public ResponseEntity<List<InAppNotificationResponse>> getUnreadNotifications(
            HttpServletRequest request) {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID userId = (UUID) request.getAttribute("userId");

        List<InAppNotificationResponse> notifications = inAppNotificationService.getUnreadNotifications(
                tenantId, userId
        );

        return ResponseEntity.ok(notifications);
    }

    /**
     * Get notification statistics (unread count, etc).
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getNotificationStats(
            HttpServletRequest request) {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID userId = (UUID) request.getAttribute("userId");

        Map<String, Long> stats = inAppNotificationService.getNotificationStats(tenantId, userId);

        return ResponseEntity.ok(stats);
    }

    /**
     * Mark a notification as read.
     */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<InAppNotificationResponse> markAsRead(
            @PathVariable UUID notificationId,
            HttpServletRequest request) {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        InAppNotificationResponse notification = inAppNotificationService.markAsRead(tenantId, notificationId);

        return ResponseEntity.ok(notification);
    }

    /**
     * Mark all notifications as read for the current user.
     */
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(HttpServletRequest request) {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID userId = (UUID) request.getAttribute("userId");

        inAppNotificationService.markAllAsRead(tenantId, userId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Delete a notification.
     */
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable UUID notificationId,
            HttpServletRequest request) {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        inAppNotificationService.deleteNotification(tenantId, notificationId);

        return ResponseEntity.noContent().build();
    }
}
