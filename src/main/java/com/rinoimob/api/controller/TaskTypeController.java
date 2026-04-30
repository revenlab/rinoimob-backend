package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.CreateTaskTypeRequest;
import com.rinoimob.domain.dto.TaskTypeResponse;
import com.rinoimob.domain.dto.UpdateTaskTypeRequest;
import com.rinoimob.service.TaskTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/task-types")
@RequiredArgsConstructor
public class TaskTypeController {

    private final TaskTypeService taskTypeService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_task_types:read')")
    public List<TaskTypeResponse> list() {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return taskTypeService.list(tenantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERMISSION_task_types:write')")
    public TaskTypeResponse create(@Valid @RequestBody CreateTaskTypeRequest req) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return taskTypeService.create(tenantId, req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_task_types:write')")
    public TaskTypeResponse update(@PathVariable UUID id, @RequestBody UpdateTaskTypeRequest req) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return taskTypeService.update(id, tenantId, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERMISSION_task_types:write')")
    public void delete(@PathVariable UUID id) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        taskTypeService.delete(id, tenantId);
    }
}
