package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.CreateTaskRequest;
import com.rinoimob.domain.dto.TaskResponse;
import com.rinoimob.domain.dto.UpdateTaskRequest;
import com.rinoimob.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_tasks:read')")
    public Page<TaskResponse> list(
            @RequestParam(required = false) Boolean pending,
            @RequestParam(required = false) UUID leadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return taskService.list(tenantId, pending, leadId, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERMISSION_tasks:write')")
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest req) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return taskService.create(tenantId, req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_tasks:write')")
    public TaskResponse update(@PathVariable UUID id, @RequestBody UpdateTaskRequest req) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return taskService.update(id, tenantId, req);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('PERMISSION_tasks:write')")
    public TaskResponse complete(@PathVariable UUID id) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return taskService.complete(id, tenantId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERMISSION_tasks:write')")
    public void delete(@PathVariable UUID id) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        taskService.delete(id, tenantId);
    }
}
