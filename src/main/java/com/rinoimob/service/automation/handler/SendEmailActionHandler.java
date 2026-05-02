package com.rinoimob.service.automation.handler;

import com.rinoimob.service.email.EmailService;
import com.rinoimob.service.automation.ActionHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SendEmailActionHandler implements ActionHandler {

    private final EmailService emailService;

    @Override
    public void execute(Map<String, Object> actionData, Map<String, Object> context,
                        Map<String, Object> resultData) throws Exception {
        Object emailObj = actionData.get("email");
        String email = emailObj != null ? emailObj.toString() : null;

        // Try context if not in action data
        if (email == null || email.isEmpty()) {
            emailObj = context.get("email");
            email = emailObj != null ? emailObj.toString() : null;
        }

        if (email == null || email.isEmpty()) {
            log.warn("Email address is missing from action data or context");
            resultData.put("email_sent", false);
            resultData.put("email_error", "Email address is required");
            return;
        }

        Object subjectObj = actionData.get("subject");
        String subject = subjectObj != null ? subjectObj.toString() : null;

        if (subject == null || subject.isEmpty()) {
            log.warn("Subject is missing from action data");
            resultData.put("email_sent", false);
            resultData.put("email_error", "Subject is required");
            return;
        }

        Object bodyObj = actionData.get("body");
        String body = bodyObj != null ? bodyObj.toString() : null;

        if (body == null || body.isEmpty()) {
            log.warn("Body is missing from action data");
            resultData.put("email_sent", false);
            resultData.put("email_error", "Body is required");
            return;
        }

        try {
            emailService.sendEmail(email, subject, body);
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
}
