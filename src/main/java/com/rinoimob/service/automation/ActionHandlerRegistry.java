package com.rinoimob.service.automation;

import com.rinoimob.domain.enums.ActionType;
import com.rinoimob.service.automation.handler.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry/factory for action handlers.
 * Maps ActionType enums to their corresponding handler implementations.
 * Uses ObjectProvider for lazy initialization to avoid circular dependencies.
 */
@Component
@Slf4j
public class ActionHandlerRegistry {

    private final ObjectProvider<SendWhatsappActionHandler> sendWhatsappActionHandlerProvider;
    private final ObjectProvider<SendEmailActionHandler> sendEmailActionHandlerProvider;
    private final ObjectProvider<CreateTaskActionHandler> createTaskActionHandlerProvider;
    private final ObjectProvider<UpdateLeadStatusActionHandler> updateLeadStatusActionHandlerProvider;
    private final ObjectProvider<AssignLeadActionHandler> assignLeadActionHandlerProvider;
    private final ObjectProvider<SendNotificationActionHandler> sendNotificationActionHandlerProvider;
    private final ObjectProvider<WaitActionHandler> waitActionHandlerProvider;

    private Map<ActionType, ActionHandler> handlers;

    public ActionHandlerRegistry(
            ObjectProvider<SendWhatsappActionHandler> sendWhatsappActionHandlerProvider,
            ObjectProvider<SendEmailActionHandler> sendEmailActionHandlerProvider,
            ObjectProvider<CreateTaskActionHandler> createTaskActionHandlerProvider,
            ObjectProvider<UpdateLeadStatusActionHandler> updateLeadStatusActionHandlerProvider,
            ObjectProvider<AssignLeadActionHandler> assignLeadActionHandlerProvider,
            ObjectProvider<SendNotificationActionHandler> sendNotificationActionHandlerProvider,
            ObjectProvider<WaitActionHandler> waitActionHandlerProvider) {
        this.sendWhatsappActionHandlerProvider = sendWhatsappActionHandlerProvider;
        this.sendEmailActionHandlerProvider = sendEmailActionHandlerProvider;
        this.createTaskActionHandlerProvider = createTaskActionHandlerProvider;
        this.updateLeadStatusActionHandlerProvider = updateLeadStatusActionHandlerProvider;
        this.assignLeadActionHandlerProvider = assignLeadActionHandlerProvider;
        this.sendNotificationActionHandlerProvider = sendNotificationActionHandlerProvider;
        this.waitActionHandlerProvider = waitActionHandlerProvider;
    }

    /**
     * Initialize the handler registry (lazy initialization).
     * Uses ObjectProvider to get beans on-demand, avoiding circular dependency issues.
     */
    private void initializeHandlers() {
        if (handlers != null) {
            return;
        }

        handlers = new HashMap<>();
        handlers.put(ActionType.SEND_WHATSAPP, sendWhatsappActionHandlerProvider.getObject());
        handlers.put(ActionType.SEND_EMAIL, sendEmailActionHandlerProvider.getObject());
        handlers.put(ActionType.CREATE_TASK, createTaskActionHandlerProvider.getObject());
        handlers.put(ActionType.UPDATE_LEAD_STATUS, updateLeadStatusActionHandlerProvider.getObject());
        handlers.put(ActionType.ASSIGN_LEAD, assignLeadActionHandlerProvider.getObject());
        handlers.put(ActionType.SEND_NOTIFICATION, sendNotificationActionHandlerProvider.getObject());
        handlers.put(ActionType.WAIT, waitActionHandlerProvider.getObject());
    }

    /**
     * Get handler for a specific action type.
     *
     * @param actionType the action type
     * @return the handler for this action type
     * @throws IllegalArgumentException if no handler exists for this action type
     */
    public ActionHandler getHandler(ActionType actionType) {
        initializeHandlers();

        ActionHandler handler = handlers.get(actionType);
        if (handler == null) {
            throw new IllegalArgumentException("No handler registered for action type: " + actionType);
        }

        return handler;
    }

    /**
     * Check if handler exists for action type.
     *
     * @param actionType the action type
     * @return true if handler exists, false otherwise
     */
    public boolean hasHandler(ActionType actionType) {
        initializeHandlers();
        return handlers.containsKey(actionType);
    }
}
