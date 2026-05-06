package com.rinoimob.service.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * In-app notification implementation.
 * Stores notifications that can be retrieved via API or WebSocket.
 * This is a placeholder for future implementation with database storage.
 */
@Service
@Slf4j
public class InAppNotificationService implements NotificationService {

    @Override
    public void sendNotification(
            UUID tenantId,
            UUID recipientId,
            String title,
            String message,
            NotificationType type,
            Map<String, Object> metadata
    ) throws Exception {
        try {
            // TODO: Implement actual in-app notification storage (database)
            // For now, log the notification intent
            log.info(
                    "In-app notification for user {}: [{}] {} - {}",
                    recipientId,
                    type,
                    title,
                    message
            );

            // Future: Store in database and broadcast via WebSocket
            // This will allow real-time delivery to connected clients
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
}
