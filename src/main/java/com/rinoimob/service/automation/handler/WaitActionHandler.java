package com.rinoimob.service.automation.handler;

import com.rinoimob.service.automation.ActionHandler;
import com.rinoimob.service.automation.workflow.WorkflowWaitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Handles WAIT action - schedules workflow continuation without blocking.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WaitActionHandler implements ActionHandler {

    private final WorkflowWaitService workflowWaitService;

    @Override
    public void execute(Map<String, Object> actionData, Map<String, Object> context,
                        Map<String, Object> resultData) throws Exception {
        Object delayObj = actionData.get("delaySeconds");
        Object executionIdObj = actionData.get("_executionId");

        if (delayObj == null) {
            throw new IllegalArgumentException("delaySeconds is required for WAIT action");
        }

        if (executionIdObj == null) {
            throw new IllegalArgumentException("executionId is required for WAIT action");
        }

        long delaySeconds;
        if (delayObj instanceof Number) {
            delaySeconds = ((Number) delayObj).longValue();
        } else if (delayObj instanceof String) {
            delaySeconds = Long.parseLong((String) delayObj);
        } else {
            throw new IllegalArgumentException("delaySeconds must be numeric");
        }

        if (delaySeconds < 0) {
            throw new IllegalArgumentException("delaySeconds must be non-negative");
        }

        UUID executionId = executionIdObj instanceof UUID
                ? (UUID) executionIdObj
                : UUID.fromString(executionIdObj.toString());

        log.info("Scheduling workflow wait of {} seconds for execution {}", delaySeconds, executionId);
        LocalDateTime resumeAt = workflowWaitService.scheduleResume(executionId, delaySeconds);

        resultData.put("waited", delaySeconds);
        resultData.put("wait_scheduled", true);
        resultData.put("wait_resume_at", resumeAt.toString());
        log.debug("Workflow execution {} scheduled to resume at {}", executionId, resumeAt);
    }
}
