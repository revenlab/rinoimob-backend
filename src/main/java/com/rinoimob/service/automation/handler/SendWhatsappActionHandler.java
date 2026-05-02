package com.rinoimob.service.automation.handler;

import com.rinoimob.domain.dto.SendWhatsappMessageRequest;
import com.rinoimob.domain.entity.Lead;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.repository.LeadRepository;
import com.rinoimob.domain.repository.UserRepository;
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
    private final UserRepository userRepository;

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
            // Try alternative key names for instanceId (for backward compatibility)
            instanceIdObj = actionData.get("whatsappInstanceId");
            if (instanceIdObj == null) {
                instanceIdObj = actionData.get("instance_id");
            }
            if (instanceIdObj == null) {
                log.warn("Instance ID is missing from action data. Available keys: {}", actionData.keySet());
                log.warn("Action data content: {}", actionData);
                resultData.put("whatsapp_sent", false);
                resultData.put("whatsapp_error", "Instance ID is required");
                return;
            }
        }

        // Validate instanceId is not empty
        String instanceIdStr = instanceIdObj.toString().trim();
        if (instanceIdStr.isEmpty()) {
            log.warn("Instance ID is empty - must select a WhatsApp instance");
            resultData.put("whatsapp_sent", false);
            resultData.put("whatsapp_error", "Instance ID is required - please select a WhatsApp instance");
            return;
        }

        // Determine recipient type (LEAD, ASSIGNED_USER, CUSTOM_NUMBER)
        String recipientType = recipientTypeObj != null ? recipientTypeObj.toString() : "LEAD";

        UUID leadId = leadIdObj instanceof String ? UUID.fromString((String) leadIdObj) : (UUID) leadIdObj;
        UUID instanceId = instanceIdStr.length() > 0 ? UUID.fromString(instanceIdStr) : null;
        
        if (instanceId == null) {
            log.warn("Failed to parse instance ID as UUID: {}", instanceIdStr);
            resultData.put("whatsapp_sent", false);
            resultData.put("whatsapp_error", "Invalid Instance ID format");
            return;
        }
        
        UUID userId = null;

        Object userIdObj = actionData.get("userId");
        if (userIdObj != null) {
            userId = userIdObj instanceof String ? UUID.fromString((String) userIdObj) : (UUID) userIdObj;
        }
        // If no userId provided, leave as null (system-generated message)

        // Extract message from multiple sources
        String message = extractMessage(actionData, context);
        if (message == null || message.isEmpty()) {
            log.warn("No message found for WhatsApp action. Tried: actionData.message, parameters.message, parameters.messageTemplate, context.name");
            resultData.put("whatsapp_sent", false);
            resultData.put("whatsapp_error", "Message not configured - please set a message, template, or provide a lead name");
            return;
        }

        try {
            String phoneNumber = null;
            
            // Get tenant ID from context (added by AutomationExecutor)
            // First try the internal context key, then fall back to TenantContext
            String tenantIdStr = null;
            Object tenantIdObj = context.get("_tenantId");
            if (tenantIdObj != null) {
                tenantIdStr = tenantIdObj.toString();
            } else {
                tenantIdStr = TenantContext.getTenantId();
            }
            
            if (tenantIdStr == null || tenantIdStr.isEmpty()) {
                log.error("Tenant ID is not available - cannot process WhatsApp action. Context keys: {}", context.keySet());
                resultData.put("whatsapp_sent", false);
                resultData.put("whatsapp_error", "Tenant context is missing");
                return;
            }
            
            UUID tenantId = UUID.fromString(tenantIdStr);

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
                    
                    // Try to get phone from assigned user first
                    phoneNumber = getAssignedUserPhone(lead.getAssignedTo(), tenantId);
                    
                    // Fallback to explicitly provided phone if not found in user
                    if (phoneNumber == null && actionData.get("recipientPhone") != null) {
                        phoneNumber = actionData.get("recipientPhone").toString();
                    }
                    
                    if (phoneNumber == null) {
                        resultData.put("whatsapp_sent", false);
                        resultData.put("whatsapp_error", "Assigned user has no phone number configured");
                        log.warn("Assigned user {} for lead {} has no phone", lead.getAssignedTo(), leadId);
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
            var response = whatsappMessageService.sendToNumber(phoneNumber, instanceId, message, userId, tenantId);
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

    private String getAssignedUserPhone(UUID userId, UUID tenantId) {
        try {
            User user = userRepository.findById(userId)
                .filter(u -> tenantId.equals(u.getTenantId()))
                .orElse(null);
            return user != null ? user.getPhone() : null;
        } catch (Exception e) {
            log.warn("Could not retrieve user {} phone: {}", userId, e.getMessage());
            return null;
        }
    }

    private String extractMessage(Map<String, Object> actionData, Map<String, Object> context) {
        // Try action data first (explicit message configuration)
        Object msg = actionData.get("message");
        if (msg != null && !msg.toString().isEmpty()) {
            return msg.toString();
        }

        // Try within parameters object (where frontend stores it)
        if (actionData.get("parameters") instanceof Map) {
            Map<String, Object> params = (Map<String, Object>) actionData.get("parameters");
            msg = params.get("message");
            if (msg != null && !msg.toString().isEmpty()) {
                return msg.toString();
            }

            // Try message template
            msg = params.get("messageTemplate");
            if (msg != null && !msg.toString().isEmpty()) {
                return msg.toString();
            }
        }

        // Try message template at top level
        msg = actionData.get("messageTemplate");
        if (msg != null && !msg.toString().isEmpty()) {
            return msg.toString();
        }

        // Try to build from context (for lead info)
        String leadName = (String) context.get("name");
        if (leadName != null && !leadName.isEmpty()) {
            return "Hi " + leadName + ", thanks for your interest in our service!";
        }

        return null;
    }
}
