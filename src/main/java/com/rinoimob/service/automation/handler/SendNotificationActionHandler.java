package com.rinoimob.service.automation.handler;

import com.rinoimob.service.automation.ActionHandler;
import com.rinoimob.service.notification.NotificationService;
import com.rinoimob.service.notification.EmailNotificationService;
import com.rinoimob.service.notification.InAppNotificationService;
import com.rinoimob.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sends notifications to users through multiple channels (email, in-app, push).
 * Supports flexible recipient configuration and notification customization.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SendNotificationActionHandler implements ActionHandler {

    private final EmailNotificationService emailNotificationService;
    private final InAppNotificationService inAppNotificationService;

    @Override
    public void execute(Map<String, Object> actionData, Map<String, Object> context,
                        Map<String, Object> resultData) throws Exception {
        String title = (String) actionData.get("title");
        String message = (String) actionData.get("message");
        String channel = (String) actionData.getOrDefault("channel", "in-app");
        String type = (String) actionData.getOrDefault("type", "INFO");

        // Provide sensible defaults if not configured
        if (title == null || title.isEmpty()) {
            title = generateDefaultTitle(context);
            log.debug("Using generated title for notification: {}", title);
        }
        if (message == null || message.isEmpty()) {
            message = generateDefaultMessage(context);
            log.debug("Using generated message for notification: {}", message);
        }

        try {
            UUID tenantId = UUID.fromString(TenantContext.getTenantId());

            // Collect notification metadata from action data and context
            Map<String, Object> metadata = buildMetadata(actionData, context);

            NotificationService.NotificationType notificationType =
                    NotificationService.NotificationType.valueOf(type.toUpperCase());

            // Determine recipient and send via specified channel
            if ("email".equalsIgnoreCase(channel)) {
                handleEmailNotification(title, message, metadata, notificationType, resultData);
            } else if ("in-app".equalsIgnoreCase(channel)) {
                handleInAppNotification(tenantId, title, message, metadata, notificationType, resultData);
            } else {
                throw new IllegalArgumentException("Unsupported notification channel: " + channel);
            }

            // Only set sent=true if it wasn't already set to false
            if (!resultData.containsKey("notification_sent") || (Boolean) resultData.get("notification_sent")) {
                resultData.put("notification_sent", true);
                resultData.put("notification_title", title);
                resultData.put("notification_channel", channel);
                log.info("Notification sent via {}: '{}' - {}", channel, title, message);
            }

        } catch (Exception e) {
            log.error("Failed to send notification: {}", e.getMessage(), e);
            resultData.put("notification_sent", false);
            resultData.put("notification_error", e.getMessage());
            throw e;
        }
    }

    private void handleEmailNotification(
            String title,
            String message,
            Map<String, Object> metadata,
            NotificationService.NotificationType type,
            Map<String, Object> resultData
    ) throws Exception {
        String email = (String) metadata.get("email");

        if (email == null || email.isEmpty()) {
            log.warn("Email address is missing. Cannot send email notification.");
            resultData.put("notification_sent", false);
            resultData.put("notification_error", "Email address is required for email channel");
            return;
        }

        emailNotificationService.sendNotificationToEmail(email, title, message, metadata);
        resultData.put("notification_recipient_email", email);
    }

    private void handleInAppNotification(
            UUID tenantId,
            String title,
            String message,
            Map<String, Object> metadata,
            NotificationService.NotificationType type,
            Map<String, Object> resultData
    ) throws Exception {
        Object userIdObj = metadata.get("userId");

        if (userIdObj == null) {
            log.warn("User ID is missing. Cannot send in-app notification.");
            resultData.put("notification_sent", false);
            resultData.put("notification_error", "User ID is required for in-app channel");
            return;
        }

        UUID recipientId = userIdObj instanceof String ? UUID.fromString((String) userIdObj) : (UUID) userIdObj;

        inAppNotificationService.sendNotification(tenantId, recipientId, title, message, type, metadata);
        resultData.put("notification_recipient_user", recipientId);
    }

    private Map<String, Object> buildMetadata(Map<String, Object> actionData, Map<String, Object> context) {
        Map<String, Object> metadata = new HashMap<>();

        // Copy relevant fields from actionData
        if (actionData.containsKey("email")) {
            metadata.put("email", actionData.get("email"));
        }
        if (actionData.containsKey("userId")) {
            metadata.put("userId", actionData.get("userId"));
        }
        if (actionData.containsKey("metadata")) {
            metadata.putAll((Map<String, Object>) actionData.get("metadata"));
        }

        // Add context information
        if (context != null) {
            if (context.containsKey("leadName")) {
                metadata.put("leadName", context.get("leadName"));
            }
            if (context.containsKey("leadId")) {
                metadata.put("leadId", context.get("leadId"));
            }
            if (context.containsKey("email")) {
                metadata.put("email", context.get("email"));
            }
        }

        return metadata;
    }

    private String generateDefaultTitle(Map<String, Object> context) {
        if (context != null && context.containsKey("leadName")) {
            return "New Lead: " + context.get("leadName");
        }
        return "Notification";
    }

    private String generateDefaultMessage(Map<String, Object> context) {
        if (context != null && context.containsKey("event")) {
            return "Automation action triggered for event: " + context.get("event");
        }
        return "You have a new notification from an automation";
    }
}
