package com.rinoimob.service.notification;

import java.util.UUID;

/**
 * Interface for sending notifications.
 * Implementations handle different notification channels (email, push, in-app).
 */
public interface NotificationService {

    /**
     * Send a notification to a user.
     *
     * @param tenantId The tenant ID (for multi-tenant context)
     * @param recipientId The user ID to receive the notification
     * @param title The notification title
     * @param message The notification message
     * @param type The notification type (info, warning, error, success)
     * @param metadata Additional metadata for the notification
     * @throws Exception if notification fails to send
     */
    void sendNotification(
            UUID tenantId,
            UUID recipientId,
            String title,
            String message,
            NotificationType type,
            java.util.Map<String, Object> metadata
    ) throws Exception;

    enum NotificationType {
        INFO, WARNING, ERROR, SUCCESS
    }
}
