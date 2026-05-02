package com.rinoimob.service.automation.handler;

import com.rinoimob.service.automation.ActionHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Handles WAIT action - introduces delay in workflow execution.
 * This handler actually executes the wait inline (blocking).
 * For true async waiting, consider using scheduled tasks instead.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WaitActionHandler implements ActionHandler {

    @Override
    public void execute(Map<String, Object> actionData, Map<String, Object> context,
                        Map<String, Object> resultData) throws Exception {
        Object delayObj = actionData.get("delaySeconds");

        if (delayObj == null) {
            throw new IllegalArgumentException("delaySeconds is required for WAIT action");
        }

        long delaySeconds = 0;
        if (delayObj instanceof Number) {
            delaySeconds = ((Number) delayObj).longValue();
        } else if (delayObj instanceof String) {
            delaySeconds = Long.parseLong((String) delayObj);
        }

        if (delaySeconds < 0) {
            throw new IllegalArgumentException("delaySeconds must be non-negative");
        }

        try {
            long delayMs = delaySeconds * 1000L;
            log.info("Workflow pausing for {} seconds", delaySeconds);
            Thread.sleep(delayMs);

            resultData.put("waited", delaySeconds);
            resultData.put("wait_completed", true);
            log.debug("Workflow resumed after {} second wait", delaySeconds);
        } catch (InterruptedException e) {
            log.warn("Wait action interrupted: {}", e.getMessage());
            resultData.put("waited", delaySeconds);
            resultData.put("wait_interrupted", true);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Wait action was interrupted", e);
        }
    }
}
