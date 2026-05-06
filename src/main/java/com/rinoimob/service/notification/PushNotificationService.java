package com.rinoimob.service.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Push notification implementation.
 * Sends notifications to mobile/web clients via push protocol.
 * This is a placeholder for future integration with Firebase Cloud Messaging or similar.
 */
@Service
@Slf4j
public class PushNotificationService implements NotificationService {

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
            // TODO: Implement actual push notification (Firebase Cloud Messaging, etc)
            // For now, log the notification intent
            log.info(
                    "Push notification for user {}: [{}] {} - {}",
                    recipientId,
                    type,
                    title,
                    message
            );

            // Future: Send via Firebase Cloud Messaging or similar service
            // This will require storing FCM tokens and managing device subscriptions
        } catch (Exception e) {
            log.error("Failed to send push notification to user {}: {}", recipientId, e.getMessage(), e);
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
        log.warn("Push notification service does not support email. Use EmailNotificationService instead.");
        throw new UnsupportedOperationException("Push notifications cannot be sent via email");
    }
}
