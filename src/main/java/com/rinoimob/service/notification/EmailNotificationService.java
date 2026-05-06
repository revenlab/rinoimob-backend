package com.rinoimob.service.notification;

import com.rinoimob.service.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Email notification implementation.
 * Sends notifications via email using the configured mail service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService implements NotificationService {

    private final EmailService emailService;

    @Override
    public void sendNotification(
            UUID tenantId,
            UUID recipientId,
            String title,
            String message,
            NotificationType type,
            Map<String, Object> metadata
    ) throws Exception {
        String email = metadata != null ? (String) metadata.get("email") : null;

        if (email == null || email.isEmpty()) {
            log.warn("Email not found in metadata for user {}. Skipping email notification.", recipientId);
            return;
        }

        sendNotificationToEmail(email, title, message, metadata);
    }

    @Override
    public void sendNotificationToEmail(
            String email,
            String title,
            String message,
            Map<String, Object> metadata
    ) throws Exception {
        try {
            String subject = title != null && !title.isEmpty() ? title : "Notification from Rinoimob";
            String body = formatEmailBody(title, message, metadata);

            emailService.sendEmail(email, subject, body);
            log.info("Notification email sent to {}", email);
        } catch (Exception e) {
            log.error("Failed to send notification email to {}: {}", email, e.getMessage(), e);
            throw e;
        }
    }

    private String formatEmailBody(String title, String message, Map<String, Object> metadata) {
        StringBuilder body = new StringBuilder();

        if (title != null && !title.isEmpty()) {
            body.append("Title: ").append(title).append("\n\n");
        }

        if (message != null && !message.isEmpty()) {
            body.append("Message: ").append(message).append("\n\n");
        }

        if (metadata != null && !metadata.isEmpty()) {
            body.append("Details:\n");
            metadata.forEach((key, value) -> {
                if (value != null && !key.equals("email")) {
                    body.append("- ").append(key).append(": ").append(value).append("\n");
                }
            });
        }

        body.append("\n---\nThis is an automated notification from Rinoimob CRM.");
        return body.toString();
    }
}
