package com.rinoimob.service.automation.handler;

import com.rinoimob.domain.dto.UpdateLeadRequest;
import com.rinoimob.domain.enums.LeadStatus;
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
public class UpdateLeadStatusActionHandler implements ActionHandler {

    private final LeadService leadService;

    @Override
    public void execute(Map<String, Object> actionData, Map<String, Object> context,
                        Map<String, Object> resultData) throws Exception {
        Object leadIdObj = context.get("leadId");

        if (leadIdObj == null) {
            log.warn("Lead ID is missing from context for status update");
            resultData.put("lead_status_updated", false);
            resultData.put("status_error", "Lead ID is required");
            return;
        }

        Object statusObj = actionData.get("newStatus");
        String newStatusStr = statusObj != null ? statusObj.toString() : null;

        if (newStatusStr == null || newStatusStr.isEmpty()) {
            log.warn("New status is missing from action data");
            resultData.put("lead_status_updated", false);
            resultData.put("status_error", "New status is required");
            return;
        }

        UUID leadId = leadIdObj instanceof String ? UUID.fromString((String) leadIdObj) : (UUID) leadIdObj;
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        try {
            LeadStatus newStatus = LeadStatus.valueOf(newStatusStr);
            UpdateLeadRequest request = new UpdateLeadRequest(null, null, null, null, newStatus, null);
            leadService.update(tenantId, leadId, request);

            resultData.put("lead_status_updated", true);
            resultData.put("lead_id", leadId);
            resultData.put("new_status", newStatusStr);
            log.info("Lead {} status updated to {} via tenant {}", leadId, newStatusStr, tenantId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid lead status: {}", newStatusStr, e);
            resultData.put("lead_status_updated", false);
            resultData.put("status_error", "Invalid status: " + newStatusStr);
            throw e;
        } catch (Exception e) {
            log.error("Failed to update lead {} status: {}", leadId, e.getMessage(), e);
            resultData.put("lead_status_updated", false);
            resultData.put("status_error", e.getMessage());
            throw e;
        }
    }
}
