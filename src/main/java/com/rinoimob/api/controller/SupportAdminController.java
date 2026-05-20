package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.SupportAuditLogResponse;
import com.rinoimob.domain.dto.SupportTenantHealthResponse;
import com.rinoimob.domain.dto.SupportTenantSummaryResponse;
import com.rinoimob.domain.dto.SupportUserSummaryResponse;
import com.rinoimob.domain.enums.SystemRole;
import com.rinoimob.service.SupportAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/support")
@Tag(name = "Support Admin", description = "System admin endpoints for Rino support team")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','SUPPORT_MANAGER','SUPPORT_AGENT')")
public class SupportAdminController {

    private final SupportAdminService supportAdminService;

    public SupportAdminController(SupportAdminService supportAdminService) {
        this.supportAdminService = supportAdminService;
    }

    @GetMapping("/tenants")
    @Operation(summary = "List all tenants for support administration")
    public List<SupportTenantSummaryResponse> listTenants(HttpServletRequest request) {
        return supportAdminService.listTenants(requireActorTenantId(request), requireActorUserId(request));
    }

    @PatchMapping("/tenants/{tenantId}/status")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Activate or deactivate a tenant")
    public SupportTenantSummaryResponse setTenantStatus(@PathVariable UUID tenantId,
                                                        @RequestParam("active") boolean active,
                                                        HttpServletRequest request) {
        UUID actorUserId = (UUID) request.getAttribute("userId");
        String actorTenantIdValue = TenantContext.getTenantId();
        if (actorUserId == null || actorTenantIdValue == null) {
            throw new IllegalStateException("Authenticated support user not found");
        }
        UUID actorTenantId = UUID.fromString(actorTenantIdValue);
        return supportAdminService.setTenantActive(actorTenantId, actorUserId, tenantId, active);
    }

    @GetMapping("/tenants/{tenantId}/users")
    @Operation(summary = "List users from a tenant for support administration")
    public List<SupportUserSummaryResponse> listTenantUsers(@PathVariable UUID tenantId, HttpServletRequest request) {
        return supportAdminService.listTenantUsers(requireActorTenantId(request), requireActorUserId(request), tenantId);
    }

    @PatchMapping("/tenants/{tenantId}/users/{userId}/status")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Activate or deactivate a tenant user")
    public SupportUserSummaryResponse setUserStatus(@PathVariable UUID tenantId,
                                                    @PathVariable UUID userId,
                                                    @RequestParam("active") boolean active,
                                                    HttpServletRequest request) {
        UUID actorUserId = (UUID) request.getAttribute("userId");
        String actorTenantIdValue = TenantContext.getTenantId();
        if (actorUserId == null || actorTenantIdValue == null) {
            throw new IllegalStateException("Authenticated support user not found");
        }
        UUID actorTenantId = UUID.fromString(actorTenantIdValue);
        return supportAdminService.setUserActive(actorTenantId, actorUserId, tenantId, userId, active);
    }

    @GetMapping("/operators")
    @Operation(summary = "List internal Rino operators")
    public List<SupportUserSummaryResponse> listOperators(HttpServletRequest request) {
        return supportAdminService.listOperators(requireActorTenantId(request), requireActorUserId(request));
    }

    @PatchMapping("/operators/{userId}/role")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Change the internal role for a support operator")
    public SupportUserSummaryResponse setOperatorRole(@PathVariable UUID userId,
                                                      @RequestParam("systemRole") SystemRole systemRole,
                                                      HttpServletRequest request) {
        UUID actorUserId = (UUID) request.getAttribute("userId");
        String actorTenantIdValue = TenantContext.getTenantId();
        if (actorUserId == null || actorTenantIdValue == null) {
            throw new IllegalStateException("Authenticated support user not found");
        }
        UUID actorTenantId = UUID.fromString(actorTenantIdValue);
        return supportAdminService.setOperatorRole(actorTenantId, actorUserId, userId, systemRole);
    }

    @GetMapping("/audit")
    @Operation(summary = "List support audit logs with filters")
    public List<SupportAuditLogResponse> listAuditLogs(@RequestParam(value = "tenantId", required = false) UUID tenantId,
                                                       @RequestParam(value = "userId", required = false) UUID userId,
                                                       @RequestParam(value = "action", required = false) String action,
                                                       @RequestParam(value = "resource", required = false) String resource,
                                                       @RequestParam(value = "from", required = false)
                                                       @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") java.time.LocalDateTime from,
                                                       @RequestParam(value = "to", required = false)
                                                       @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") java.time.LocalDateTime to,
                                                       HttpServletRequest request) {
        return supportAdminService.listAuditLogs(
                requireActorTenantId(request),
                requireActorUserId(request),
                tenantId,
                userId,
                action,
                resource,
                from,
                to
        );
    }

    @GetMapping("/tenants/{tenantId}/health")
    @Operation(summary = "Get tenant health summary")
    public SupportTenantHealthResponse getTenantHealth(@PathVariable UUID tenantId, HttpServletRequest request) {
        return supportAdminService.getTenantHealth(requireActorTenantId(request), requireActorUserId(request), tenantId);
    }

    @PostMapping("/tenants/{tenantId}/users/{userId}/resend-invitation")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Resend a pending invitation for a tenant user")
    public SupportUserSummaryResponse resendInvitation(@PathVariable UUID tenantId,
                                                       @PathVariable UUID userId,
                                                       HttpServletRequest request) {
        return supportAdminService.resendInvitation(requireActorTenantId(request), requireActorUserId(request), tenantId, userId);
    }

    @PostMapping("/tenants/{tenantId}/users/{userId}/reset-access")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Force password reset on next login and invalidate current tokens for a tenant user")
    public SupportUserSummaryResponse resetAccess(@PathVariable UUID tenantId,
                                                  @PathVariable UUID userId,
                                                  HttpServletRequest request) {
        return supportAdminService.resetAccess(requireActorTenantId(request), requireActorUserId(request), tenantId, userId);
    }

    private UUID requireActorUserId(HttpServletRequest request) {
        UUID actorUserId = (UUID) request.getAttribute("userId");
        if (actorUserId == null) {
            throw new IllegalStateException("Authenticated support user not found");
        }

        return actorUserId;
    }

    private UUID requireActorTenantId(HttpServletRequest request) {
        String actorTenantIdValue = TenantContext.getTenantId();
        if (actorTenantIdValue == null) {
            throw new IllegalStateException("Authenticated support user not found");
        }

        return UUID.fromString(actorTenantIdValue);
    }
}
