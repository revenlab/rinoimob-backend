package com.rinoimob.service;

import com.rinoimob.domain.entity.AutomationWorkflow;
import com.rinoimob.domain.entity.Lead;
import com.rinoimob.domain.entity.Task;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.enums.LeadStatus;
import com.rinoimob.domain.enums.TriggerType;
import com.rinoimob.domain.repository.AutomationWorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutomationEventDispatcher {

    private final AutomationWorkflowRepository workflowRepository;
    private final AutomationExecutor automationExecutor;

    @Async
    public void dispatchLeadCreated(Lead lead) {
        log.debug("Dispatching LEAD_CREATED event for lead: {}", lead.getId());

        List<AutomationWorkflow> workflows = workflowRepository.findByTenantIdAndIsActiveTrue(lead.getTenantId());

        for (AutomationWorkflow workflow : workflows) {
            if (isTriggerEnabled(workflow, TriggerType.LEAD_CREATED)) {
                Map<String, Object> triggerData = buildLeadTriggerData(lead);
                automationExecutor.executeWorkflow(workflow, TriggerType.LEAD_CREATED.name(), triggerData);
            }
        }
    }

    @Async
    public void dispatchLeadStatusChanged(Lead lead, LeadStatus oldStatus) {
        log.debug("Dispatching LEAD_STATUS_CHANGED event for lead: {} (old: {}, new: {})",
                lead.getId(), oldStatus, lead.getStatus());

        List<AutomationWorkflow> workflows = workflowRepository.findByTenantIdAndIsActiveTrue(lead.getTenantId());

        for (AutomationWorkflow workflow : workflows) {
            if (isTriggerEnabled(workflow, TriggerType.LEAD_STATUS_CHANGED)) {
                Map<String, Object> triggerData = buildLeadTriggerData(lead);
                triggerData.put("oldStatus", oldStatus != null ? oldStatus.name() : null);
                automationExecutor.executeWorkflow(workflow, TriggerType.LEAD_STATUS_CHANGED.name(), triggerData);
            }
        }
    }

    @Async
    public void dispatchLeadAssigned(Lead lead, User assignedUser) {
        log.debug("Dispatching LEAD_ASSIGNED event for lead: {} to user: {}",
                lead.getId(), assignedUser != null ? assignedUser.getId() : null);

        List<AutomationWorkflow> workflows = workflowRepository.findByTenantIdAndIsActiveTrue(lead.getTenantId());

        for (AutomationWorkflow workflow : workflows) {
            if (isTriggerEnabled(workflow, TriggerType.LEAD_ASSIGNED)) {
                Map<String, Object> triggerData = buildLeadTriggerData(lead);
                if (assignedUser != null) {
                    triggerData.put("assignedUserId", assignedUser.getId());
                    String fullName = (assignedUser.getFirstName() != null ? assignedUser.getFirstName() : "") + " " +
                            (assignedUser.getLastName() != null ? assignedUser.getLastName() : "");
                    triggerData.put("assignedUserName", fullName.trim());
                }
                automationExecutor.executeWorkflow(workflow, TriggerType.LEAD_ASSIGNED.name(), triggerData);
            }
        }
    }

    @Async
    public void dispatchTaskCreated(Task task) {
        log.debug("Dispatching TASK_CREATED event for task: {}", task.getId());

        List<AutomationWorkflow> workflows = workflowRepository.findByTenantIdAndIsActiveTrue(task.getTenantId());

        for (AutomationWorkflow workflow : workflows) {
            if (isTriggerEnabled(workflow, TriggerType.TASK_CREATED)) {
                Map<String, Object> triggerData = buildTaskTriggerData(task);
                automationExecutor.executeWorkflow(workflow, TriggerType.TASK_CREATED.name(), triggerData);
            }
        }
    }

    @Async
    public void dispatchTaskOverdue(Task task) {
        log.debug("Dispatching TASK_OVERDUE event for task: {}", task.getId());

        List<AutomationWorkflow> workflows = workflowRepository.findByTenantIdAndIsActiveTrue(task.getTenantId());

        for (AutomationWorkflow workflow : workflows) {
            if (isTriggerEnabled(workflow, TriggerType.TASK_OVERDUE)) {
                Map<String, Object> triggerData = buildTaskTriggerData(task);
                automationExecutor.executeWorkflow(workflow, TriggerType.TASK_OVERDUE.name(), triggerData);
            }
        }
    }

    @Async
    public void dispatchTaskCompleted(Task task) {
        log.debug("Dispatching TASK_COMPLETED event for task: {}", task.getId());

        List<AutomationWorkflow> workflows = workflowRepository.findByTenantIdAndIsActiveTrue(task.getTenantId());

        for (AutomationWorkflow workflow : workflows) {
            if (isTriggerEnabled(workflow, TriggerType.TASK_COMPLETED)) {
                Map<String, Object> triggerData = buildTaskTriggerData(task);
                automationExecutor.executeWorkflow(workflow, TriggerType.TASK_COMPLETED.name(), triggerData);
            }
        }
    }

    private boolean isTriggerEnabled(AutomationWorkflow workflow, TriggerType triggerType) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.rinoimob.domain.dto.WorkflowConfigDto config = mapper.readValue(workflow.getWorkflowConfig(),
                    com.rinoimob.domain.dto.WorkflowConfigDto.class);

            return config.getNodes().stream()
                    .anyMatch(node -> node.getType() == com.rinoimob.domain.enums.NodeType.TRIGGER &&
                            node.getData() != null &&
                            triggerType.name().equals(node.getData().get("triggerType")));
        } catch (Exception e) {
            log.error("Error checking trigger type in workflow {}: {}", workflow.getId(), e.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildLeadTriggerData(Lead lead) {
        Map<String, Object> data = new HashMap<>();
        data.put("leadId", lead.getId().toString());
        data.put("name", lead.getName());
        data.put("email", lead.getEmail());
        data.put("phone", lead.getPhone());
        data.put("status", lead.getStatus() != null ? lead.getStatus().name() : null);
        data.put("source", lead.getSource());
        data.put("assignedTo", lead.getAssignedTo() != null ? lead.getAssignedTo().toString() : null);
        return data;
    }

    private Map<String, Object> buildTaskTriggerData(Task task) {
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getId().toString());
        data.put("title", task.getTitle());
        data.put("description", task.getDescription());
        if (task.getDueAt() != null) {
            data.put("dueDate", task.getDueAt().toString());
        }
        data.put("status", task.getCompletedAt() != null ? "COMPLETED" : "PENDING");
        data.put("leadId", task.getLeadId() != null ? task.getLeadId().toString() : null);
        return data;
    }
}
