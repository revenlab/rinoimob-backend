package com.rinoimob.service.automation.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.*;
import com.rinoimob.domain.entity.AutomationExecution;
import com.rinoimob.domain.entity.AutomationWorkflow;
import com.rinoimob.domain.enums.*;
import com.rinoimob.domain.repository.AutomationExecutionRepository;
import com.rinoimob.domain.repository.AutomationWorkflowRepository;
import com.rinoimob.service.automation.ActionHandler;
import com.rinoimob.service.automation.ActionHandlerRegistry;
import com.rinoimob.service.billing.TenantPlanAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutomationExecutor {

    private final AutomationExecutionRepository automationExecutionRepository;
    private final AutomationWorkflowRepository workflowRepository;
    private final ObjectMapper objectMapper;
    private final ActionHandlerRegistry actionHandlerRegistry;
    private final TenantPlanAccessService tenantPlanAccessService;

    @Transactional
    public AutomationExecutionResponse executeWorkflow(AutomationWorkflow workflow, String triggerEvent,
                                                       Map<String, Object> triggerData) {
        tenantPlanAccessService.requireEnabled(workflow.getTenantId(), BillingFeature.AUTOMATION_CRM);
        AutomationExecution execution = new AutomationExecution();
        execution.setWorkflowId(workflow.getId());
        execution.setTenantId(workflow.getTenantId());
        execution.setTriggerEvent(triggerEvent);
        execution.setStatus(WorkflowExecutionStatus.RUNNING);

        TenantContext.setTenantId(workflow.getTenantId().toString());
        try {
            execution.setTriggerData(objectMapper.writeValueAsString(triggerData));
            execution = automationExecutionRepository.saveAndFlush(execution);

            WorkflowConfigDto config = objectMapper.readValue(workflow.getWorkflowConfig(),
                    WorkflowConfigDto.class);

            List<String> executionPath = new ArrayList<>();
            Map<String, Object> resultData = new HashMap<>();

            Map<String, Object> context = new HashMap<>(triggerData);
            context.put("_tenantId", workflow.getTenantId().toString());

            boolean paused = executeGraph(config, context, executionPath, resultData, execution);

            execution.setExecutionPath(objectMapper.writeValueAsString(executionPath));
            execution.setResultData(objectMapper.writeValueAsString(resultData));
            if (paused) {
                execution.setStatus(WorkflowExecutionStatus.WAITING);
                execution.setResumeAt(resolveResumeAt(resultData));
            } else {
                execution.setStatus(WorkflowExecutionStatus.COMPLETED);
                execution.setCompletedAt(LocalDateTime.now());
                execution.setResumeAt(null);
            }
        } catch (Exception e) {
            log.error("Error executing workflow {}: {}", workflow.getId(), e.getMessage(), e);
            execution.setStatus(WorkflowExecutionStatus.FAILED);
            execution.setErrorMessage(e.getMessage());
            execution.setCompletedAt(LocalDateTime.now());
            execution.setResumeAt(null);
        } finally {
            TenantContext.clear();
        }

        AutomationExecution savedExecution = automationExecutionRepository.save(execution);
        return mapToResponse(savedExecution);
    }

    @Transactional
    public AutomationExecutionResponse resumeWaitingExecution(UUID executionId) {
        AutomationExecution execution = automationExecutionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Automation execution not found: " + executionId));

        if (!WorkflowExecutionStatus.WAITING.equals(execution.getStatus())) {
            return mapToResponse(execution);
        }

        if (!tenantPlanAccessService.isEnabled(execution.getTenantId(), BillingFeature.AUTOMATION_CRM)) {
            execution.setStatus(WorkflowExecutionStatus.FAILED);
            execution.setErrorMessage("Automação interrompida porque o plano atual não inclui Automações CRM");
            execution.setCompletedAt(LocalDateTime.now());
            execution.setResumeAt(null);
            return mapToResponse(automationExecutionRepository.save(execution));
        }

        TenantContext.setTenantId(execution.getTenantId().toString());
        try {
            execution.setStatus(WorkflowExecutionStatus.RUNNING);
            automationExecutionRepository.save(execution);

            AutomationWorkflow workflow = workflowRepository.findByTenantIdAndId(execution.getTenantId(),
                            execution.getWorkflowId())
                    .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + execution.getWorkflowId()));

            WorkflowConfigDto config = objectMapper.readValue(workflow.getWorkflowConfig(),
                    WorkflowConfigDto.class);

            Map<String, Object> context = readMap(execution.getTriggerData());
            context.put("_tenantId", execution.getTenantId().toString());

            List<String> executionPath = readPath(execution.getExecutionPath());
            Map<String, Object> resultData = readMap(execution.getResultData());

            if (executionPath.isEmpty()) {
                throw new IllegalStateException("Cannot resume execution without a recorded path");
            }

            Map<String, WorkflowNodeDto> nodeMap = new HashMap<>();
            config.getNodes().forEach(n -> nodeMap.put(n.getId(), n));

            String lastNodeId = executionPath.get(executionPath.size() - 1);
            boolean paused = findAndExecuteNextNodes(lastNodeId, config, nodeMap, context, executionPath, resultData,
                    execution);

            execution.setExecutionPath(objectMapper.writeValueAsString(executionPath));
            execution.setResultData(objectMapper.writeValueAsString(resultData));
            if (paused) {
                execution.setStatus(WorkflowExecutionStatus.WAITING);
                execution.setResumeAt(resolveResumeAt(resultData));
            } else {
                execution.setStatus(WorkflowExecutionStatus.COMPLETED);
                execution.setCompletedAt(LocalDateTime.now());
                execution.setResumeAt(null);
            }
        } catch (Exception e) {
            log.error("Error resuming workflow execution {}: {}", executionId, e.getMessage(), e);
            execution.setStatus(WorkflowExecutionStatus.FAILED);
            execution.setErrorMessage(e.getMessage());
            execution.setCompletedAt(LocalDateTime.now());
            execution.setResumeAt(null);
        } finally {
            TenantContext.clear();
        }

        AutomationExecution savedExecution = automationExecutionRepository.save(execution);
        return mapToResponse(savedExecution);
    }

    private boolean executeGraph(WorkflowConfigDto config, Map<String, Object> context, List<String> executionPath,
                                 Map<String, Object> resultData, AutomationExecution execution) {
        Map<String, WorkflowNodeDto> nodeMap = new HashMap<>();
        config.getNodes().forEach(n -> nodeMap.put(n.getId(), n));

        String triggerId = config.getNodes().stream()
                .filter(n -> NodeType.TRIGGER.equals(n.getType()))
                .findFirst()
                .map(WorkflowNodeDto::getId)
                .orElse(null);

        if (triggerId != null) {
            return executeNode(triggerId, config, nodeMap, context, executionPath, resultData, execution);
        }
        return false;
    }

    private boolean executeNode(String nodeId, WorkflowConfigDto config, Map<String, WorkflowNodeDto> nodeMap,
                                Map<String, Object> context, List<String> executionPath,
                                Map<String, Object> resultData, AutomationExecution execution) {
        if (!nodeMap.containsKey(nodeId)) {
            return false;
        }

        WorkflowNodeDto node = nodeMap.get(nodeId);
        executionPath.add(nodeId);

        if (NodeType.TRIGGER.equals(node.getType())) {
            return findAndExecuteNextNodes(nodeId, config, nodeMap, context, executionPath, resultData, execution);
        } else if (NodeType.CONDITION.equals(node.getType())) {
            boolean conditionMet = evaluateCondition(node, context);
            String nextBranch = conditionMet ? "yes" : "no";
            return findAndExecuteNextNodes(nodeId, config, nodeMap, context, executionPath, resultData, execution,
                    nextBranch);
        } else if (NodeType.ACTION.equals(node.getType())) {
            boolean paused = executeAction(node, context, resultData, execution);
            if (paused) {
                return true;
            }
            return findAndExecuteNextNodes(nodeId, config, nodeMap, context, executionPath, resultData, execution);
        }

        return false;
    }

    private boolean findAndExecuteNextNodes(String nodeId, WorkflowConfigDto config,
                                            Map<String, WorkflowNodeDto> nodeMap,
                                            Map<String, Object> context, List<String> executionPath,
                                            Map<String, Object> resultData, AutomationExecution execution) {
        for (WorkflowEdgeDto edge : config.getEdges()) {
            if (edge.getSource().equals(nodeId) && (edge.getLabel() == null || edge.getLabel().isEmpty())) {
                if (executeNode(edge.getTarget(), config, nodeMap, context, executionPath, resultData, execution)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean findAndExecuteNextNodes(String nodeId, WorkflowConfigDto config,
                                            Map<String, WorkflowNodeDto> nodeMap,
                                            Map<String, Object> context, List<String> executionPath,
                                            Map<String, Object> resultData, AutomationExecution execution,
                                            String label) {
        for (WorkflowEdgeDto edge : config.getEdges()) {
            if (edge.getSource().equals(nodeId) && label.equals(edge.getLabel())) {
                if (executeNode(edge.getTarget(), config, nodeMap, context, executionPath, resultData, execution)) {
                    return true;
                }
            }
        }
        return false;
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

    private boolean executeAction(WorkflowNodeDto node, Map<String, Object> context,
                                  Map<String, Object> resultData, AutomationExecution execution) {
        Map<String, Object> data = node.getData();
        if (data == null || data.isEmpty()) {
            return false;
        }

        String actionType = (String) data.get("actionType");
        if (actionType == null) {
            return false;
        }

        Map<String, Object> actionData = new HashMap<>(data);
        Object parameters = data.get("parameters");
        if (parameters instanceof Map) {
            actionData.putAll((Map<String, Object>) parameters);
        }

        try {
            ActionType type = ActionType.valueOf(actionType);
            actionData.put("_executionId", execution.getId());
            executeActionByType(type, actionData, context, resultData);
            return ActionType.WAIT.equals(type) && Boolean.TRUE.equals(resultData.get("wait_scheduled"));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown action type: {}", actionType);
            return false;
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
        response.setResumeAt(execution.getResumeAt());

        try {
            if (execution.getExecutionPath() != null) {
                response.setExecutionPath(readPath(execution.getExecutionPath()));
            }

            if (execution.getResultData() != null) {
                response.setResultData(readMap(execution.getResultData()));
            }
        } catch (Exception e) {
            log.warn("Error deserializing execution data: {}", e.getMessage());
        }

        return response;
    }

    private List<String> readPath(String json) throws Exception {
        if (json == null) {
            return new ArrayList<>();
        }
        return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    }

    private Map<String, Object> readMap(String json) throws Exception {
        if (json == null) {
            return new HashMap<>();
        }
        return objectMapper.readValue(json, objectMapper.getTypeFactory().constructMapType(Map.class, String.class,
                Object.class));
    }

    private LocalDateTime resolveResumeAt(Map<String, Object> resultData) {
        Object value = resultData.get("wait_resume_at");
        if (value == null) {
            return null;
        }
        return LocalDateTime.parse(value.toString());
    }
}
