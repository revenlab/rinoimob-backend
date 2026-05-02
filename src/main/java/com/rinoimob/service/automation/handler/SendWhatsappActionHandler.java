package com.rinoimob.service.automation.handler;

import com.rinoimob.domain.dto.SendWhatsappMessageRequest;
import com.rinoimob.domain.entity.Lead;
import com.rinoimob.domain.repository.LeadRepository;
import com.rinoimob.service.WhatsappMessageService;
import com.rinoimob.service.automation.ActionHandler;
import com.rinoimob.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SendWhatsappActionHandler implements ActionHandler {

    private final WhatsappMessageService whatsappMessageService;
    private final LeadRepository leadRepository;

    @Override
    public void execute(Map<String, Object> actionData, Map<String, Object> context,
                        Map<String, Object> resultData) throws Exception {
        Object leadIdObj = context.get("leadId");
        Object instanceIdObj = actionData.get("instanceId");
        Object recipientTypeObj = actionData.get("recipientType");

        if (leadIdObj == null) {
            log.warn("Lead ID is missing from context, cannot send WhatsApp");
            resultData.put("whatsapp_sent", false);
            resultData.put("whatsapp_error", "Lead ID is required");
            return;
        }

        if (instanceIdObj == null) {
            log.warn("Instance ID is missing from action data, cannot send WhatsApp");
            resultData.put("whatsapp_sent", false);
            resultData.put("whatsapp_error", "Instance ID is required");
            return;
        }

        // Determine recipient type (LEAD, ASSIGNED_USER, CUSTOM_NUMBER)
        String recipientType = recipientTypeObj != null ? recipientTypeObj.toString() : "LEAD";

        UUID leadId = leadIdObj instanceof String ? UUID.fromString((String) leadIdObj) : (UUID) leadIdObj;
        UUID instanceId = instanceIdObj instanceof String ? UUID.fromString((String) instanceIdObj) : (UUID) instanceIdObj;
        UUID userId = null;

        Object userIdObj = actionData.get("userId");
        if (userIdObj != null) {
            userId = userIdObj instanceof String ? UUID.fromString((String) userIdObj) : (UUID) userIdObj;
        } else {
            userId = UUID.randomUUID();
        }

        // Extract message from multiple sources
        String message = extractMessage(actionData, context);
        if (message == null || message.isEmpty()) {
            log.warn("No message found for WhatsApp action (tried: message, messageTemplate, context fields)");
            resultData.put("whatsapp_sent", false);
            resultData.put("whatsapp_error", "Message not configured");
            return;
        }

        try {
            String phoneNumber = null;
            UUID tenantId = UUID.fromString(TenantContext.getTenantId());

            switch (recipientType.toUpperCase()) {
                case "LEAD":
                    phoneNumber = getLeadPhone(leadId, tenantId);
                    if (phoneNumber == null) {
                        resultData.put("whatsapp_sent", false);
                        resultData.put("whatsapp_error", "Lead has no phone number configured");
                        log.warn("Lead {} has no phone number", leadId);
                        return;
                    }
                    break;

                case "ASSIGNED_USER":
                    Lead lead = getLeadWithAssignee(leadId, tenantId);
                    if (lead.getAssignedTo() == null) {
                        resultData.put("whatsapp_sent", false);
                        resultData.put("whatsapp_error", "Lead has no assigned user");
                        log.warn("Lead {} has no assigned user", leadId);
                        return;
                    }
                    phoneNumber = actionData.get("recipientPhone") != null ? 
                        actionData.get("recipientPhone").toString() : null;
                    if (phoneNumber == null) {
                        resultData.put("whatsapp_sent", false);
                        resultData.put("whatsapp_error", "Assigned user phone number not provided");
                        log.warn("Assigned user phone for lead {} not found", leadId);
                        return;
                    }
                    break;

                case "CUSTOM_NUMBER":
                    phoneNumber = actionData.get("recipientValue") != null ?
                        actionData.get("recipientValue").toString() : null;
                    if (phoneNumber == null || phoneNumber.isEmpty()) {
                        resultData.put("whatsapp_sent", false);
                        resultData.put("whatsapp_error", "Custom recipient phone number not provided");
                        log.warn("Custom phone number not provided for WhatsApp action");
                        return;
                    }
                    break;

                default:
                    resultData.put("whatsapp_sent", false);
                    resultData.put("whatsapp_error", "Invalid recipient type: " + recipientType);
                    log.warn("Unknown recipient type: {}", recipientType);
                    return;
            }

            // Send the message
            var response = whatsappMessageService.sendToNumber(phoneNumber, instanceId, message, userId);
            resultData.put("whatsapp_sent", true);
            resultData.put("whatsapp_message_id", response.getId());
            resultData.put("whatsapp_recipient_type", recipientType);
            resultData.put("whatsapp_recipient", phoneNumber);
            log.info("WhatsApp message sent successfully via {} to {} (instance: {}, type: {})",
                message, phoneNumber, instanceId, recipientType);

        } catch (Exception e) {
            log.error("Failed to send WhatsApp message: {}", e.getMessage(), e);
            resultData.put("whatsapp_sent", false);
            resultData.put("whatsapp_error", e.getMessage());
            throw e;
        }
    }

    private String getLeadPhone(UUID leadId, UUID tenantId) {
        try {
            Lead lead = leadRepository.findByIdAndTenantIdAndDeletedAtIsNull(leadId, tenantId)
                .orElse(null);
            return lead != null ? lead.getPhone() : null;
        } catch (Exception e) {
            log.warn("Could not retrieve lead {} phone: {}", leadId, e.getMessage());
            return null;
        }
    }

    private Lead getLeadWithAssignee(UUID leadId, UUID tenantId) throws Exception {
        try {
            return leadRepository.findByIdAndTenantIdAndDeletedAtIsNull(leadId, tenantId)
                .orElseThrow(() -> new RuntimeException("Lead not found"));
        } catch (Exception e) {
            log.warn("Could not retrieve lead {}: {}", leadId, e.getMessage());
            throw new RuntimeException("Lead not found", e);
        }
    }

    private String extractMessage(Map<String, Object> actionData, Map<String, Object> context) {
        // Try action data first (explicit message configuration)
        Object msg = actionData.get("message");
        if (msg != null && !msg.toString().isEmpty()) {
            return msg.toString();
        }

        // Try message template
        msg = actionData.get("messageTemplate");
        if (msg != null && !msg.toString().isEmpty()) {
            return msg.toString();
        }

        // Try to build from context (for lead info)
        String leadName = (String) context.get("leadName");
        if (leadName != null && !leadName.isEmpty()) {
            return "Hi " + leadName + ", thanks for your interest in our service!";
        }

        return null;
    }
}
