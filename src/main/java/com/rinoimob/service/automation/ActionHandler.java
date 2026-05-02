package com.rinoimob.service.automation;

import java.util.Map;

/**
 * Interface for all automation action handlers.
 * Implementations execute specific automation actions (send WhatsApp, email, create task, etc.)
 */
public interface ActionHandler {

    /**
     * Execute the action with provided data and context.
     *
     * @param actionData Data specific to this action (e.g., phone number, message template)
     * @param context Trigger context (lead, task, user data from the trigger event)
     * @param resultData Map to store action results (populated by handler)
     * @throws Exception if action execution fails
     */
    void execute(Map<String, Object> actionData, Map<String, Object> context,
                 Map<String, Object> resultData) throws Exception;
}
