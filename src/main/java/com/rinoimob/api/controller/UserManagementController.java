package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.InviteUserRequest;
import com.rinoimob.domain.dto.UpdateUserRoleRequest;
import com.rinoimob.domain.dto.UserManagementResponse;
import com.rinoimob.service.UserManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/team/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserManagementService userManagementService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_users:read')")
    public ResponseEntity<List<UserManagementResponse>> listUsers() {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(userManagementService.listUsers(tenantId));
    }

    @PostMapping("/invite")
    @PreAuthorize("hasAuthority('PERMISSION_users:write')")
    public ResponseEntity<UserManagementResponse> inviteUser(@RequestBody InviteUserRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(userManagementService.inviteUser(tenantId, request));
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasAuthority('PERMISSION_users:write')")
    public ResponseEntity<UserManagementResponse> assignRole(
            @PathVariable UUID id,
            @RequestBody UpdateUserRoleRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(userManagementService.assignRole(tenantId, id, request.roleId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_users:write')")
    public ResponseEntity<Void> deactivateUser(@PathVariable UUID id) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        userManagementService.deactivateUser(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
