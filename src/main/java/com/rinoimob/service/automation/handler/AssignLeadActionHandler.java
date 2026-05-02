package com.rinoimob.service.automation.handler;

import com.rinoimob.domain.dto.UpdateLeadRequest;
import com.rinoimob.service.LeadService;
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
public class AssignLeadActionHandler implements ActionHandler {

    private final LeadService leadService;

    @Override
    public void execute(Map<String, Object> actionData, Map<String, Object> context,
                        Map<String, Object> resultData) throws Exception {
        Object leadIdObj = context.get("leadId");
        Object userIdObj = actionData.get("userId");

        if (leadIdObj == null) {
            log.warn("Lead ID is missing from context for assignment");
            resultData.put("lead_assigned", false);
            resultData.put("assign_error", "Lead ID is required");
            return;
        }

        if (userIdObj == null) {
            log.warn("User ID is missing from action data for assignment");
            resultData.put("lead_assigned", false);
            resultData.put("assign_error", "User ID is required");
            return;
        }

        UUID leadId = leadIdObj instanceof String ? UUID.fromString((String) leadIdObj) : (UUID) leadIdObj;
        UUID userId = userIdObj instanceof String ? UUID.fromString((String) userIdObj) : (UUID) userIdObj;
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        try {
            UpdateLeadRequest request = new UpdateLeadRequest(null, null, null, null, null, userId);
            leadService.update(tenantId, leadId, request);

            resultData.put("lead_assigned", true);
            resultData.put("lead_id", leadId);
            resultData.put("assigned_to_user", userId);
            log.info("Lead {} assigned to user {} via tenant {}", leadId, userId, tenantId);
        } catch (Exception e) {
            log.error("Failed to assign lead {}: {}", leadId, e.getMessage(), e);
            resultData.put("lead_assigned", false);
            resultData.put("assign_error", e.getMessage());
            throw e;
        }
    }
}
