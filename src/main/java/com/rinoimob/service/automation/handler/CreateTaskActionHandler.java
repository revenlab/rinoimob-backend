package com.rinoimob.service.automation.handler;

import com.rinoimob.domain.dto.CreateTaskRequest;
import com.rinoimob.service.TaskService;
import com.rinoimob.service.automation.ActionHandler;
import com.rinoimob.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreateTaskActionHandler implements ActionHandler {

    private final TaskService taskService;

    @Override
    public void execute(Map<String, Object> actionData, Map<String, Object> context,
                        Map<String, Object> resultData) throws Exception {
        Object titleObj = actionData.get("title");
        String title = titleObj != null ? titleObj.toString() : null;

        if (title == null || title.isEmpty()) {
            // Try to generate from context
            Object leadNameObj = context.get("leadName");
            if (leadNameObj != null) {
                title = "Follow-up with " + leadNameObj;
            } else {
                log.warn("Task title is missing from action data and context");
                resultData.put("task_created", false);
                resultData.put("task_error", "Task title is required");
                return;
            }
        }

        Object descriptionObj = actionData.get("description");
        String description = descriptionObj != null ? descriptionObj.toString() : null;

        Object leadIdObj = context.get("leadId");
        UUID leadId = null;
        if (leadIdObj != null) {
            leadId = leadIdObj instanceof String ? UUID.fromString((String) leadIdObj) : (UUID) leadIdObj;
        }

        Object assignedToObj = actionData.get("assignedTo");
        UUID assignedTo = null;
        if (assignedToObj != null) {
            assignedTo = assignedToObj instanceof String ? UUID.fromString((String) assignedToObj) : (UUID) assignedToObj;
        }

        Object dueAtObj = actionData.get("dueAt");
        LocalDateTime dueAt = null;
        if (dueAtObj != null && dueAtObj instanceof LocalDateTime) {
            dueAt = (LocalDateTime) dueAtObj;
        }

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        try {
            CreateTaskRequest request = new CreateTaskRequest(title, description, leadId, assignedTo, dueAt, null);
            var taskResponse = taskService.create(tenantId, request);

            resultData.put("task_created", true);
            resultData.put("task_id", taskResponse.id());
            log.info("Task created successfully with ID: {} for lead: {} via tenant {}",
                    taskResponse.id(), leadId, tenantId);
        } catch (Exception e) {
            log.error("Failed to create task: {}", e.getMessage(), e);
            resultData.put("task_created", false);
            resultData.put("task_error", e.getMessage());
            throw e;
        }
    }
}

