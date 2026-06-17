package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.CreateTaskRequest;
import com.rinoimob.domain.dto.TaskResponse;
import com.rinoimob.domain.dto.UpdateTaskRequest;
import com.rinoimob.service.crm.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Collection;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    private UUID resolveScopedUserId(Authentication auth, HttpServletRequest request, boolean readOperation) {
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        boolean hasAll = authorities.stream().anyMatch(a ->
                a.getAuthority().equals(readOperation ? "PERMISSION_tasks:read_all" : "PERMISSION_tasks:write_all") ||
                a.getAuthority().equals(readOperation ? "PERMISSION_tasks:read" : "PERMISSION_tasks:write"));
        if (hasAll) return null;
        return (UUID) request.getAttribute("userId");
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERMISSION_tasks:read','PERMISSION_tasks:read_all','PERMISSION_tasks:read_own')")
    public Page<TaskResponse> list(
            @RequestParam(required = false) Boolean pending,
            @RequestParam(required = false) UUID leadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth,
            HttpServletRequest request
    ) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID scopedUserId = resolveScopedUserId(auth, request, true);
        return taskService.list(tenantId, scopedUserId, pending, leadId, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('PERMISSION_tasks:write','PERMISSION_tasks:write_all','PERMISSION_tasks:write_own')")
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest req, Authentication auth, HttpServletRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID scopedUserId = resolveScopedUserId(auth, request, false);
        return taskService.create(tenantId, scopedUserId, req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERMISSION_tasks:write','PERMISSION_tasks:write_all','PERMISSION_tasks:write_own')")
    public TaskResponse update(@PathVariable UUID id, @RequestBody UpdateTaskRequest req, Authentication auth, HttpServletRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID scopedUserId = resolveScopedUserId(auth, request, false);
        return taskService.update(id, tenantId, scopedUserId, req);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyAuthority('PERMISSION_tasks:write','PERMISSION_tasks:write_all','PERMISSION_tasks:write_own')")
    public TaskResponse complete(@PathVariable UUID id, Authentication auth, HttpServletRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID scopedUserId = resolveScopedUserId(auth, request, false);
        return taskService.complete(id, tenantId, scopedUserId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('PERMISSION_tasks:write','PERMISSION_tasks:write_all','PERMISSION_tasks:write_own')")
    public void delete(@PathVariable UUID id, Authentication auth, HttpServletRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID scopedUserId = resolveScopedUserId(auth, request, false);
        taskService.delete(id, tenantId, scopedUserId);
    }
}
