package com.rinoimob.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.domain.dto.*;
import com.rinoimob.domain.entity.AutomationExecution;
import com.rinoimob.domain.entity.AutomationWorkflow;
import com.rinoimob.domain.enums.*;
import com.rinoimob.domain.repository.AutomationExecutionRepository;
import com.rinoimob.service.automation.ActionHandler;
import com.rinoimob.service.automation.ActionHandlerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutomationExecutor {

    private final AutomationExecutionRepository automationExecutionRepository;
    private final ObjectMapper objectMapper;
    private final ActionHandlerRegistry actionHandlerRegistry;

    @Transactional
    public AutomationExecutionResponse executeWorkflow(AutomationWorkflow workflow, String triggerEvent,
                                                        Map<String, Object> triggerData) {
        AutomationExecution execution = new AutomationExecution();
        execution.setWorkflowId(workflow.getId());
        execution.setTenantId(workflow.getTenantId());
        execution.setTriggerEvent(triggerEvent);
        execution.setStatus(WorkflowExecutionStatus.RUNNING);

        try {
            execution.setTriggerData(objectMapper.writeValueAsString(triggerData));

            WorkflowConfigDto config = objectMapper.readValue(workflow.getWorkflowConfig(),
                    WorkflowConfigDto.class);

            List<String> executionPath = new ArrayList<>();
            Map<String, Object> resultData = new HashMap<>();
            
            // Add tenantId to context so handlers can access it
            Map<String, Object> context = new HashMap<>(triggerData);
            context.put("_tenantId", workflow.getTenantId().toString());

            executeGraph(config, context, executionPath, resultData);

            execution.setExecutionPath(objectMapper.writeValueAsString(executionPath));
            execution.setResultData(objectMapper.writeValueAsString(resultData));
            execution.setStatus(WorkflowExecutionStatus.COMPLETED);
            execution.setCompletedAt(LocalDateTime.now());

        } catch (Exception e) {
            log.error("Error executing workflow {}: {}", workflow.getId(), e.getMessage(), e);
            execution.setStatus(WorkflowExecutionStatus.FAILED);
            execution.setErrorMessage(e.getMessage());
            execution.setCompletedAt(LocalDateTime.now());
        }

        AutomationExecution savedExecution = automationExecutionRepository.save(execution);
        return mapToResponse(savedExecution);
    }

    private void executeGraph(WorkflowConfigDto config, Map<String, Object> context, List<String> executionPath,
                              Map<String, Object> resultData) {
        Map<String, WorkflowNodeDto> nodeMap = new HashMap<>();
        config.getNodes().forEach(n -> nodeMap.put(n.getId(), n));

        String triggerId = config.getNodes().stream()
                .filter(n -> NodeType.TRIGGER.equals(n.getType()))
                .findFirst()
                .map(WorkflowNodeDto::getId)
                .orElse(null);

        if (triggerId != null) {
            executeNode(triggerId, config, nodeMap, context, executionPath, resultData);
        }
    }

    private void executeNode(String nodeId, WorkflowConfigDto config, Map<String, WorkflowNodeDto> nodeMap,
                             Map<String, Object> context, List<String> executionPath,
                             Map<String, Object> resultData) {
        if (!nodeMap.containsKey(nodeId)) {
            return;
        }

        WorkflowNodeDto node = nodeMap.get(nodeId);
        executionPath.add(nodeId);

        if (NodeType.TRIGGER.equals(node.getType())) {
            findAndExecuteNextNodes(nodeId, config, nodeMap, context, executionPath, resultData);
        } else if (NodeType.CONDITION.equals(node.getType())) {
            boolean conditionMet = evaluateCondition(node, context);
            String nextBranch = conditionMet ? "yes" : "no";
            findAndExecuteNextNodes(nodeId, config, nodeMap, context, executionPath, resultData, nextBranch);
        } else if (NodeType.ACTION.equals(node.getType())) {
            executeAction(node, context, resultData);
            findAndExecuteNextNodes(nodeId, config, nodeMap, context, executionPath, resultData);
        }
    }

    private void findAndExecuteNextNodes(String nodeId, WorkflowConfigDto config,
                                        Map<String, WorkflowNodeDto> nodeMap,
                                        Map<String, Object> context, List<String> executionPath,
                                        Map<String, Object> resultData) {
        for (WorkflowEdgeDto edge : config.getEdges()) {
            if (edge.getSource().equals(nodeId) && (edge.getLabel() == null || edge.getLabel().isEmpty())) {
                executeNode(edge.getTarget(), config, nodeMap, context, executionPath, resultData);
            }
        }
    }

    private void findAndExecuteNextNodes(String nodeId, WorkflowConfigDto config,
                                        Map<String, WorkflowNodeDto> nodeMap,
                                        Map<String, Object> context, List<String> executionPath,
                                        Map<String, Object> resultData, String label) {
        for (WorkflowEdgeDto edge : config.getEdges()) {
            if (edge.getSource().equals(nodeId) && label.equals(edge.getLabel())) {
                executeNode(edge.getTarget(), config, nodeMap, context, executionPath, resultData);
            }
        }
    }

    private boolean evaluateCondition(WorkflowNodeDto node, Map<String, Object> context) {
        Map<String, Object> data = node.getData();
        if (data == null || data.isEmpty()) {
            return true;
        }

        String conditionType = (String) data.get("conditionType");
        if (conditionType == null) {
            return true;
        }

        try {
            ConditionType type = ConditionType.valueOf(conditionType);
            return evaluateConditionByType(type, data, context);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown condition type: {}", conditionType);
            return true;
        }
    }

    private boolean evaluateConditionByType(ConditionType type, Map<String, Object> data,
                                            Map<String, Object> context) {
        switch (type) {
            case LEAD_STATUS_IS:
                String expectedStatus = (String) data.get("value");
                String actualStatus = (String) context.get("status");
                return expectedStatus != null && expectedStatus.equals(actualStatus);

            case LEAD_ASSIGNED_TO:
                String expectedAssignee = (String) data.get("userId");
                String actualAssignee = (String) context.get("assignedTo");
                return expectedAssignee != null && expectedAssignee.equals(actualAssignee);

            case CUSTOM_FIELD_VALUE:
                String fieldName = (String) data.get("fieldName");
                Object expectedValue = data.get("expectedValue");
                Object actualValue = context.get(fieldName);
                return expectedValue != null && expectedValue.equals(actualValue);

            default:
                return true;
        }
    }

    private void executeAction(WorkflowNodeDto node, Map<String, Object> context,
                               Map<String, Object> resultData) {
        Map<String, Object> data = node.getData();
        if (data == null || data.isEmpty()) {
            return;
        }

        String actionType = (String) data.get("actionType");
        if (actionType == null) {
            return;
        }

        // Flatten parameters into data if they exist (for backward compatibility)
        Map<String, Object> actionData = new HashMap<>(data);
        Object parameters = data.get("parameters");
        if (parameters instanceof Map) {
            actionData.putAll((Map<String, Object>) parameters);
        }

        try {
            ActionType type = ActionType.valueOf(actionType);
            executeActionByType(type, actionData, context, resultData);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown action type: {}", actionType);
        }
    }

    private void executeActionByType(ActionType type, Map<String, Object> data, Map<String, Object> context,
                                     Map<String, Object> resultData) {
        try {
            log.debug("Executing action type: {}, with data keys: {}", type, data.keySet());
            ActionHandler handler = actionHandlerRegistry.getHandler(type);
            handler.execute(data, context, resultData);
            log.info("Action {} executed successfully", type);
        } catch (Exception e) {
            log.error("Error executing action {}: {}", type, e.getMessage(), e);
            resultData.put("action_error", type.name());
            resultData.put("action_error_message", e.getMessage());
            throw new RuntimeException("Action execution failed: " + e.getMessage(), e);
        }
    }

    private AutomationExecutionResponse mapToResponse(AutomationExecution execution) {
        AutomationExecutionResponse response = new AutomationExecutionResponse();
        response.setId(execution.getId());
        response.setWorkflowId(execution.getWorkflowId());
        response.setTriggerEvent(execution.getTriggerEvent());
        response.setStatus(execution.getStatus());
        response.setErrorMessage(execution.getErrorMessage());
        response.setCreatedAt(execution.getCreatedAt());
        response.setCompletedAt(execution.getCompletedAt());

        try {
            if (execution.getExecutionPath() != null) {
                List<String> path = objectMapper.readValue(execution.getExecutionPath(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                response.setExecutionPath(path);
            }

            if (execution.getResultData() != null) {
                Map<String, Object> result = objectMapper.readValue(execution.getResultData(),
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                response.setResultData(result);
            }
        } catch (Exception e) {
            log.warn("Error deserializing execution data: {}", e.getMessage());
        }

        return response;
    }
}
