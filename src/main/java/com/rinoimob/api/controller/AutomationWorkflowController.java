package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.*;
import com.rinoimob.service.AutomationWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/automation-workflows")
@RequiredArgsConstructor
public class AutomationWorkflowController {

    private final AutomationWorkflowService automationWorkflowService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_settings:manage')")
    public ResponseEntity<List<AutomationWorkflowResponse>> listWorkflows() {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(automationWorkflowService.listWorkflows(tenantId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_settings:manage')")
    public ResponseEntity<AutomationWorkflowResponse> getWorkflow(@PathVariable UUID id) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(automationWorkflowService.getWorkflow(tenantId, id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_settings:manage')")
    public ResponseEntity<AutomationWorkflowResponse> createWorkflow(
            @Valid @RequestBody CreateAutomationWorkflowRequest request,
            HttpServletRequest httpRequest) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID userId = (UUID) httpRequest.getAttribute("userId");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(automationWorkflowService.createWorkflow(tenantId, request, userId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_settings:manage')")
    public ResponseEntity<AutomationWorkflowResponse> updateWorkflow(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAutomationWorkflowRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(automationWorkflowService.updateWorkflow(tenantId, id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_settings:manage')")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable UUID id) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        automationWorkflowService.deleteWorkflow(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasAuthority('PERMISSION_settings:manage')")
    public ResponseEntity<AutomationWorkflowResponse> toggleActive(
            @PathVariable UUID id,
            @RequestParam Boolean isActive) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(automationWorkflowService.toggleActive(tenantId, id, isActive));
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAuthority('PERMISSION_settings:manage')")
    public ResponseEntity<AutomationExecutionResponse> testWorkflow(
            @PathVariable UUID id,
            @Valid @RequestBody TestWorkflowRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(automationWorkflowService.testWorkflow(tenantId, id, request));
    }

    @GetMapping("/{id}/executions")
    @PreAuthorize("hasAuthority('PERMISSION_settings:manage')")
    public ResponseEntity<List<AutomationExecutionResponse>> getExecutionHistory(@PathVariable UUID id) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(automationWorkflowService.getExecutionHistory(tenantId, id));
    }
}
