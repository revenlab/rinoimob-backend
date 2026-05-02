package com.rinoimob.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.domain.dto.*;
import com.rinoimob.domain.entity.AutomationWorkflow;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.repository.AutomationExecutionRepository;
import com.rinoimob.domain.repository.AutomationWorkflowRepository;
import com.rinoimob.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutomationWorkflowService {

    private final AutomationWorkflowRepository workflowRepository;
    private final AutomationExecutionRepository executionRepository;
    private final UserRepository userRepository;
    private final WorkflowGraphValidator graphValidator;
    private final AutomationExecutor automationExecutor;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<AutomationWorkflowResponse> listWorkflows(UUID tenantId) {
        List<AutomationWorkflow> workflows = workflowRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return workflows.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AutomationWorkflowResponse getWorkflow(UUID tenantId, UUID id) {
        AutomationWorkflow workflow = findOwned(tenantId, id);
        return toResponse(workflow);
    }

    @Transactional
    public AutomationWorkflowResponse createWorkflow(UUID tenantId, CreateAutomationWorkflowRequest request,
                                                      UUID userId) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Workflow name is required");
        }

        if (request.getWorkflowConfig() == null) {
            throw new IllegalArgumentException("Workflow configuration is required");
        }

        if (workflowRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Workflow with this name already exists in this tenant");
        }

        validateWorkflowConfig(request.getWorkflowConfig());

        AutomationWorkflow workflow = new AutomationWorkflow();
        workflow.setTenantId(tenantId);
        workflow.setName(request.getName());
        workflow.setDescription(request.getDescription());
        workflow.setIsActive(true);
        workflow.setCreatedById(userId);
        workflow.setVersion(1);

        try {
            workflow.setWorkflowConfig(objectMapper.writeValueAsString(request.getWorkflowConfig()));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize workflow configuration: " + e.getMessage());
        }

        AutomationWorkflow saved = workflowRepository.save(workflow);
        log.info("Created workflow {} for tenant {}", saved.getId(), tenantId);
        return toResponse(saved);
    }

    @Transactional
    public AutomationWorkflowResponse updateWorkflow(UUID tenantId, UUID id,
                                                      UpdateAutomationWorkflowRequest request) {
        AutomationWorkflow workflow = findOwned(tenantId, id);

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            if (!request.getName().equals(workflow.getName()) &&
                    workflowRepository.existsByTenantIdAndName(tenantId, request.getName())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Another workflow with this name already exists");
            }
            workflow.setName(request.getName());
        }

        if (request.getDescription() != null) {
            workflow.setDescription(request.getDescription());
        }

        if (request.getWorkflowConfig() != null) {
            validateWorkflowConfig(request.getWorkflowConfig());
            try {
                workflow.setWorkflowConfig(objectMapper.writeValueAsString(request.getWorkflowConfig()));
                workflow.setVersion(workflow.getVersion() + 1);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to serialize workflow configuration: " + e.getMessage());
            }
        }

        if (request.getIsActive() != null) {
            workflow.setIsActive(request.getIsActive());
        }

        AutomationWorkflow updated = workflowRepository.save(workflow);
        log.info("Updated workflow {} for tenant {}", updated.getId(), tenantId);
        return toResponse(updated);
    }

    @Transactional
    public void deleteWorkflow(UUID tenantId, UUID id) {
        AutomationWorkflow workflow = findOwned(tenantId, id);
        workflowRepository.delete(workflow);
        log.info("Deleted workflow {} for tenant {}", id, tenantId);
    }

    @Transactional
    public AutomationWorkflowResponse toggleActive(UUID tenantId, UUID id, Boolean isActive) {
        AutomationWorkflow workflow = findOwned(tenantId, id);
        workflow.setIsActive(isActive != null ? isActive : !workflow.getIsActive());
        AutomationWorkflow updated = workflowRepository.save(workflow);
        log.info("Toggled workflow {} active state to {} for tenant {}", id, updated.getIsActive(), tenantId);
        return toResponse(updated);
    }

    @Transactional
    public AutomationExecutionResponse testWorkflow(UUID tenantId, UUID id, TestWorkflowRequest request) {
        AutomationWorkflow workflow = findOwned(tenantId, id);

        if (request.getTriggerEvent() == null || request.getTriggerEvent().trim().isEmpty()) {
            throw new IllegalArgumentException("Trigger event is required");
        }

        if (request.getTriggerData() == null) {
            request.setTriggerData(java.util.Collections.emptyMap());
        }

        log.info("Testing workflow {} with trigger event: {}", id, request.getTriggerEvent());
        return automationExecutor.executeWorkflow(workflow, request.getTriggerEvent(), request.getTriggerData());
    }

    @Transactional(readOnly = true)
    public List<AutomationExecutionResponse> getExecutionHistory(UUID tenantId, UUID workflowId) {
        AutomationWorkflow workflow = findOwned(tenantId, workflowId);
        return executionRepository.findByWorkflowIdOrderByCreatedAtDesc(workflowId)
                .stream()
                .map(this::mapExecutionToResponse)
                .toList();
    }

    private void validateWorkflowConfig(WorkflowConfigDto config) {
        if (config == null) {
            throw new IllegalArgumentException("Workflow configuration cannot be null");
        }
        graphValidator.validate(config);
    }

    private AutomationWorkflow findOwned(UUID tenantId, UUID id) {
        return workflowRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Workflow not found"));
    }

    private AutomationWorkflowResponse toResponse(AutomationWorkflow workflow) {
        AutomationWorkflowResponse response = new AutomationWorkflowResponse();
        response.setId(workflow.getId());
        response.setName(workflow.getName());
        response.setDescription(workflow.getDescription());
        response.setIsActive(workflow.getIsActive());
        response.setVersion(workflow.getVersion());
        response.setCreatedAt(workflow.getCreatedAt());
        response.setUpdatedAt(workflow.getUpdatedAt());

        if (workflow.getCreatedById() != null) {
            Optional<User> user = userRepository.findById(workflow.getCreatedById());
            response.setCreatedBy(user.map(u -> {
                String fullName = (u.getFirstName() != null ? u.getFirstName() : "") + " " +
                        (u.getLastName() != null ? u.getLastName() : "");
                return fullName.trim();
            }).orElse(null));
        }

        try {
            WorkflowConfigDto config = objectMapper.readValue(workflow.getWorkflowConfig(),
                    WorkflowConfigDto.class);
            response.setWorkflowConfig(config);
        } catch (Exception e) {
            log.warn("Error deserializing workflow config for workflow {}: {}", workflow.getId(), e.getMessage());
        }

        return response;
    }

    private AutomationExecutionResponse mapExecutionToResponse(com.rinoimob.domain.entity.AutomationExecution execution) {
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
                java.util.Map<String, Object> result = objectMapper.readValue(execution.getResultData(),
                        objectMapper.getTypeFactory().constructMapType(java.util.Map.class, String.class, Object.class));
                response.setResultData(result);
            }
        } catch (Exception e) {
            log.warn("Error deserializing execution data: {}", e.getMessage());
        }

        return response;
    }
}
