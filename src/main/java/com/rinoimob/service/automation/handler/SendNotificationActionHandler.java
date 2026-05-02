package com.rinoimob.service.automation.handler;

import com.rinoimob.service.automation.ActionHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Sends notifications to users.
 * This is a placeholder for notification service integration.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SendNotificationActionHandler implements ActionHandler {

    @Override
    public void execute(Map<String, Object> actionData, Map<String, Object> context,
                        Map<String, Object> resultData) throws Exception {
        String title = (String) actionData.get("title");
        String message = (String) actionData.get("message");
        String userId = (String) actionData.get("userId");

        if (title == null || title.isEmpty()) {
            throw new IllegalArgumentException("Notification title is required");
        }
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("Notification message is required");
        }

        try {
            // TODO: Implement actual notification service
            // For now, log the notification intent
            resultData.put("notification_sent", true);
            resultData.put("notification_title", title);
            if (userId != null) {
                resultData.put("notification_recipient", userId);
            }
            log.info("Notification scheduled: '{}' - {}", title, message);
        } catch (Exception e) {
            log.error("Failed to send notification: {}", e.getMessage(), e);
            resultData.put("notification_sent", false);
            resultData.put("notification_error", e.getMessage());
            throw e;
        }
    }
}
