package com.rinoimob.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.domain.dto.InAppNotificationResponse;
import com.rinoimob.domain.entity.InAppNotification;
import com.rinoimob.domain.repository.InAppNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * In-app notification implementation with database storage.
 * Sends notifications to users and broadcasts via WebSocket for real-time delivery.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InAppNotificationService implements NotificationService {

    private final InAppNotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void sendNotification(
            UUID tenantId,
            UUID recipientId,
            String title,
            String message,
            NotificationType type,
            Map<String, Object> metadata
    ) throws Exception {
        try {
            InAppNotification notification = new InAppNotification();
            notification.setTenantId(tenantId);
            notification.setRecipientId(recipientId);
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setType(InAppNotification.NotificationType.valueOf(type.name()));
            notification.setIsRead(false);
            notification.setCreatedAt(LocalDateTime.now());

            if (metadata != null) {
                String metadataJson = (String) metadata.getOrDefault("metadata", null);
                if (metadataJson != null) {
                    notification.setMetadata(metadataJson);
                }

                String actionUrl = (String) metadata.get("actionUrl");
                if (actionUrl != null) {
                    notification.setActionUrl(actionUrl);
                }
            }

            InAppNotification saved = notificationRepository.save(notification);

            // Broadcast via WebSocket to connected clients
            broadcastToUser(recipientId, saved);

            log.info("In-app notification stored for user {}: '{}' - {}", recipientId, title, message);
        } catch (Exception e) {
            log.error("Failed to store in-app notification for user {}: {}", recipientId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void sendNotificationToEmail(
            String email,
            String title,
            String message,
            Map<String, Object> metadata
    ) throws Exception {
        log.warn("In-app notification service does not support email. Use EmailNotificationService instead.");
        throw new UnsupportedOperationException("In-app notifications cannot be sent via email");
    }

    /**
     * Get paginated notifications for a user.
     */
    @Transactional(readOnly = true)
    public Page<InAppNotificationResponse> getUserNotifications(
            UUID tenantId,
            UUID userId,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<InAppNotification> notifications = notificationRepository.findByTenantIdAndRecipientId(
                tenantId, userId, pageable
        );

        return notifications.map(this::toResponse);
    }

    /**
     * Get unread notifications for a user.
     */
    @Transactional(readOnly = true)
    public List<InAppNotificationResponse> getUnreadNotifications(
            UUID tenantId,
            UUID userId
    ) {
        List<InAppNotification> notifications = notificationRepository.findUnreadByTenantIdAndRecipientId(
                tenantId, userId
        );

        return notifications.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get notification statistics for a user.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getNotificationStats(UUID tenantId, UUID userId) {
        long unreadCount = notificationRepository.countUnreadByTenantIdAndRecipientId(tenantId, userId);

        return Map.of(
                "unreadCount", unreadCount
        );
    }

    /**
     * Mark a notification as read.
     */
    @Transactional
    public InAppNotificationResponse markAsRead(UUID tenantId, UUID notificationId) {
        InAppNotification notification = notificationRepository.findByIdAndTenantId(notificationId, tenantId);

        if (notification == null) {
            throw new IllegalArgumentException("Notification not found");
        }

        notification.setIsRead(true);
        notification.setReadAt(LocalDateTime.now());
        InAppNotification saved = notificationRepository.save(notification);

        return toResponse(saved);
    }

    /**
     * Mark all notifications as read for a user.
     */
    @Transactional
    public void markAllAsRead(UUID tenantId, UUID userId) {
        notificationRepository.markAllAsRead(tenantId, userId);
        log.info("All notifications marked as read for user {} in tenant {}", userId, tenantId);
    }

    /**
     * Delete a notification.
     */
    @Transactional
    public void deleteNotification(UUID tenantId, UUID notificationId) {
        InAppNotification notification = notificationRepository.findByIdAndTenantId(notificationId, tenantId);

        if (notification == null) {
            throw new IllegalArgumentException("Notification not found");
        }

        notificationRepository.delete(notification);
        log.info("Notification {} deleted for tenant {}", notificationId, tenantId);
    }

    /**
     * Broadcast notification to user via WebSocket.
     */
    private void broadcastToUser(UUID userId, InAppNotification notification) {
        try {
            String destination = "/user/" + userId + "/queue/notifications";
            InAppNotificationResponse response = toResponse(notification);
            messagingTemplate.convertAndSend(destination, response);
            log.debug("Notification broadcasted to user {} via WebSocket", userId);
        } catch (Exception e) {
            log.warn("Failed to broadcast notification via WebSocket: {}", e.getMessage());
        }
    }

    private InAppNotificationResponse toResponse(InAppNotification notification) {
        return InAppNotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType().name())
                .isRead(notification.getIsRead())
                .readAt(notification.getReadAt())
                .metadata(notification.getMetadata())
                .actionUrl(notification.getActionUrl())
                .createdAt(notification.getCreatedAt())
                .updatedAt(notification.getUpdatedAt())
                .build();
    }
}

