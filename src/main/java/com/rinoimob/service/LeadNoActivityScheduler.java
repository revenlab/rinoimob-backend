package com.rinoimob.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.domain.dto.WorkflowConfigDto;
import com.rinoimob.domain.dto.WorkflowNodeDto;
import com.rinoimob.domain.entity.AutomationWorkflow;
import com.rinoimob.domain.entity.Lead;
import com.rinoimob.domain.enums.LeadStatus;
import com.rinoimob.domain.enums.NodeType;
import com.rinoimob.domain.enums.TriggerType;
import com.rinoimob.domain.repository.AutomationExecutionRepository;
import com.rinoimob.domain.repository.AutomationWorkflowRepository;
import com.rinoimob.domain.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadNoActivityScheduler {

    private static final List<LeadStatus> CLOSED_STATUSES = List.of(LeadStatus.LOST, LeadStatus.WON);
    private static final int DEFAULT_INACTIVE_DAYS = 7;

    private final AutomationWorkflowRepository workflowRepository;
    private final LeadRepository leadRepository;
    private final AutomationExecutionRepository automationExecutionRepository;
    private final AutomationExecutor automationExecutor;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${automation.lead-no-activity-scan-ms:600000}")
    public void scanInactiveLeads() {
        List<AutomationWorkflow> workflows = workflowRepository.findAll().stream()
                .filter(workflow -> Boolean.TRUE.equals(workflow.getIsActive()))
                .toList();

        if (workflows.isEmpty()) {
            return;
        }

        for (AutomationWorkflow workflow : workflows) {
            WorkflowConfigDto config = readConfig(workflow);
            if (config == null || config.getNodes() == null) {
                continue;
            }

            WorkflowNodeDto triggerNode = config.getNodes().stream()
                    .filter(node -> node.getType() == NodeType.TRIGGER)
                    .filter(node -> node.getData() != null)
                    .filter(node -> TriggerType.LEAD_NO_ACTIVITY.name().equals(node.getData().get("triggerType")))
                    .findFirst()
                    .orElse(null);

            if (triggerNode == null) {
                continue;
            }

            int inactiveDays = resolveInactiveDays(triggerNode.getData());
            if (inactiveDays <= 0) {
                log.debug("Skipping workflow {} because inactiveDays is invalid: {}", workflow.getId(), inactiveDays);
                continue;
            }

            LocalDateTime cutoff = LocalDateTime.now().minusDays(inactiveDays);
            List<Lead> leads = leadRepository.findByTenantIdAndDeletedAtIsNullAndStatusNotInAndUpdatedAtBefore(
                    workflow.getTenantId(), CLOSED_STATUSES, cutoff);

            for (Lead lead : leads) {
                if (automationExecutionRepository.existsLeadTriggerExecutionAfter(
                        workflow.getId(),
                        TriggerType.LEAD_NO_ACTIVITY.name(),
                        lead.getId().toString(),
                        lead.getUpdatedAt())) {
                    continue;
                }

                log.debug("Dispatching LEAD_NO_ACTIVITY for lead {} on workflow {}", lead.getId(), workflow.getId());
                automationExecutor.executeWorkflow(workflow, TriggerType.LEAD_NO_ACTIVITY.name(),
                        buildTriggerData(lead, inactiveDays, cutoff));
            }
        }
    }

    private WorkflowConfigDto readConfig(AutomationWorkflow workflow) {
        try {
            return objectMapper.readValue(workflow.getWorkflowConfig(), WorkflowConfigDto.class);
        } catch (Exception e) {
            log.error("Error parsing workflow config {}: {}", workflow.getId(), e.getMessage());
            return null;
        }
    }

    private int resolveInactiveDays(Map<String, Object> triggerData) {
        Object parameters = triggerData.get("parameters");
        if (parameters instanceof Map<?, ?> parameterMap) {
            Integer parsed = firstPositiveInteger(parameterMap, "inactiveDays", "days", "delayDays");
            if (parsed != null) {
                return parsed;
            }
        }

        Integer direct = firstPositiveInteger(triggerData, "inactiveDays", "days", "delayDays");
        return direct != null ? direct : DEFAULT_INACTIVE_DAYS;
    }

    private Integer firstPositiveInteger(Map<?, ?> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value instanceof Number number) {
                int parsed = number.intValue();
                if (parsed > 0) {
                    return parsed;
                }
            } else if (value instanceof String text) {
                try {
                    int parsed = Integer.parseInt(text);
                    if (parsed > 0) {
                        return parsed;
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore invalid values and continue trying other keys.
                }
            }
        }
        return null;
    }

    private Map<String, Object> buildTriggerData(Lead lead, int inactiveDays, LocalDateTime cutoff) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("leadId", lead.getId().toString());
        data.put("name", lead.getName());
        data.put("email", lead.getEmail());
        data.put("phone", lead.getPhone());
        data.put("status", lead.getStatus() != null ? lead.getStatus().name() : null);
        data.put("source", lead.getSource());
        data.put("assignedTo", lead.getAssignedTo() != null ? lead.getAssignedTo().toString() : null);
        data.put("inactiveDays", inactiveDays);
        data.put("inactiveSince", cutoff.truncatedTo(ChronoUnit.SECONDS).toString());
        data.put("lastActivityAt", lead.getUpdatedAt() != null ? lead.getUpdatedAt().truncatedTo(ChronoUnit.SECONDS).toString() : null);
        return data;
    }
}
