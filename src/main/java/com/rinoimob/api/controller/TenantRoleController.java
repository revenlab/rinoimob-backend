package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.CreateTenantRoleRequest;
import com.rinoimob.domain.dto.TenantRoleResponse;
import com.rinoimob.domain.dto.UpdateTenantRoleRequest;
import com.rinoimob.service.TenantRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class TenantRoleController {

    private final TenantRoleService tenantRoleService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_roles:read') or hasAuthority('PERMISSION_roles:write')")
    public ResponseEntity<List<TenantRoleResponse>> listRoles() {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(tenantRoleService.listRoles(tenantId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_roles:read') or hasAuthority('PERMISSION_roles:write')")
    public ResponseEntity<TenantRoleResponse> getRole(@PathVariable UUID id) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(tenantRoleService.getRole(tenantId, id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_roles:write')")
    public ResponseEntity<TenantRoleResponse> createRole(@RequestBody CreateTenantRoleRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantRoleService.createRole(tenantId, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_roles:write')")
    public ResponseEntity<TenantRoleResponse> updateRole(@PathVariable UUID id, @RequestBody UpdateTenantRoleRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(tenantRoleService.updateRole(tenantId, id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_roles:write')")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        tenantRoleService.deleteRole(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
