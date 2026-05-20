package com.rinoimob.service.automation.handler;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.entity.EmailSenderConfig;
import com.rinoimob.domain.entity.Lead;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.repository.LeadRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.EmailSenderConfigService;
import com.rinoimob.service.email.EmailService;
import com.rinoimob.service.automation.ActionHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SendEmailActionHandler implements ActionHandler {

    private final EmailService emailService;
    private final EmailSenderConfigService emailSenderConfigService;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final Environment environment;

    @Override
    public void execute(Map<String, Object> actionData, Map<String, Object> context,
                        Map<String, Object> resultData) throws Exception {
        String recipientType = stringValue(actionData.get("recipientType"));
        String tenantIdStr = stringValue(context.get("_tenantId"));
        if (tenantIdStr == null || tenantIdStr.isBlank()) {
            tenantIdStr = TenantContext.getTenantId();
        }

        String email = resolveRecipientEmail(recipientType, actionData, context, tenantIdStr);
        if (email == null || email.isBlank()) {
            log.warn("Recipient email is missing for SEND_EMAIL (recipientType={}, contextKeys={})",
                    recipientType, context.keySet());
            resultData.put("email_sent", false);
            resultData.put("email_error", "Recipient email is required");
            return;
        }

        String subject = stringValue(actionData.get("subject"));
        if (subject == null || subject.isBlank()) {
            log.warn("Subject is missing from action data");
            resultData.put("email_sent", false);
            resultData.put("email_error", "Subject is required");
            return;
        }

        String body = stringValue(actionData.get("body"));
        if (body == null || body.isBlank()) {
            log.warn("Body is missing from action data");
            resultData.put("email_sent", false);
            resultData.put("email_error", "Body is required");
            return;
        }

        try {
            EmailSenderConfig senderConfig = resolveSenderConfig(actionData, tenantIdStr);
            if (senderConfig != null) {
                emailService.sendEmailWithConfig(senderConfig, email, subject, body);
            } else if (isDevProfile()) {
                emailService.sendEmail(email, subject, body);
            } else {
                throw new IllegalStateException("Workflow emails require a configured SMTP sender");
            }
            resultData.put("email_sent", true);
            resultData.put("email_to", email);
            log.info("Email sent successfully to {}", email);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", email, e.getMessage(), e);
            resultData.put("email_sent", false);
            resultData.put("email_error", e.getMessage());
            throw e;
        }
    }

    private boolean isDevProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "dev".equalsIgnoreCase(profile));
    }

    private EmailSenderConfig resolveSenderConfig(Map<String, Object> actionData, String tenantIdStr) {
        String senderConfigIdStr = stringValue(actionData.get("senderConfigId"));
        if (senderConfigIdStr == null || senderConfigIdStr.isBlank()) {
            return null;
        }
        UUID configId = parseUuid(senderConfigIdStr);
        UUID tenantId = parseUuid(tenantIdStr);
        if (configId == null || tenantId == null) {
            return null;
        }
        try {
            return emailSenderConfigService.resolveForTenant(configId, tenantId);
        } catch (Exception e) {
            log.warn("Could not resolve sender config {}: {}", senderConfigIdStr, e.getMessage());
            return null;
        }
    }

    private String resolveRecipientEmail(String recipientType, Map<String, Object> actionData,
                                         Map<String, Object> context, String tenantIdStr) {
        String resolvedType = recipientType != null && !recipientType.isBlank() ? recipientType : "LEGACY";
        switch (resolvedType.toUpperCase()) {
            case "ASSIGNED_USER":
                return resolveAssignedUserEmail(actionData, context, tenantIdStr);
            case "LEAD":
                return resolveLeadEmail(actionData, context, tenantIdStr);
            case "CUSTOM_EMAIL":
                return firstNonBlank(stringValue(actionData.get("recipientEmail")), stringValue(actionData.get("email")));
            default:
                return firstNonBlank(stringValue(actionData.get("email")), stringValue(context.get("email")));
        }
    }

    private String resolveLeadEmail(Map<String, Object> actionData, Map<String, Object> context, String tenantIdStr) {
        String directEmail = firstNonBlank(stringValue(context.get("email")), stringValue(context.get("leadEmail")));
        if (directEmail != null) {
            return directEmail;
        }

        UUID tenantId = parseUuid(tenantIdStr);
        UUID leadId = parseUuid(stringValue(context.get("leadId")));
        if (tenantId == null || leadId == null) {
            return null;
        }

        return leadRepository.findByIdAndTenantIdAndDeletedAtIsNull(leadId, tenantId)
                .map(Lead::getEmail)
                .orElse(null);
    }

    private String resolveAssignedUserEmail(Map<String, Object> actionData, Map<String, Object> context, String tenantIdStr) {
        UUID tenantId = parseUuid(tenantIdStr);
        UUID userId = parseUuid(stringValue(context.get("assignedTo")));
        if (tenantId != null && userId != null) {
            Optional<User> user = userRepository.findById(userId)
                    .filter(u -> tenantId.equals(u.getTenantId()));
            if (user.isPresent()) {
                return user.get().getEmail();
            }
        }

        return firstNonBlank(stringValue(actionData.get("email")), stringValue(context.get("email")));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value != null ? value.toString().trim() : null;
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
