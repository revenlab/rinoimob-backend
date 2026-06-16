package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.SetOperatorPermissionsRequest;
import com.rinoimob.domain.dto.SupportAuditLogResponse;
import com.rinoimob.domain.dto.SupportDashboardResponse;
import com.rinoimob.domain.dto.SupportTenantBillingResponse;
import com.rinoimob.domain.dto.SupportTenantHealthResponse;
import com.rinoimob.domain.dto.SupportTenantSummaryResponse;
import com.rinoimob.domain.dto.SupportUserSummaryResponse;
import com.rinoimob.domain.dto.TenantWebsiteConfigResponse;
import com.rinoimob.domain.dto.UpdateSupportTenantRequest;
import com.rinoimob.domain.dto.UpdateSupportTenantBillingRequest;
import com.rinoimob.domain.dto.UpdateSupportUserRequest;
import com.rinoimob.domain.dto.UpdateTenantWebsiteConfigRequest;
import com.rinoimob.domain.dto.blog.BlogPostResponse;
import com.rinoimob.domain.dto.blog.CreateBlogPostRequest;
import com.rinoimob.domain.dto.blog.UpdateBlogPostRequest;
import com.rinoimob.domain.dto.blog.UpdateBlogPostStatusRequest;
import com.rinoimob.domain.enums.SystemRole;
import com.rinoimob.service.website.BlogPostService;
import com.rinoimob.service.support.SupportAdminService;
import com.rinoimob.service.website.TenantWebsiteConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/support")
@Tag(name = "Support Admin", description = "System admin endpoints for Rino support team")
@RequiredArgsConstructor
public class SupportAdminController {

    private final SupportAdminService supportAdminService;
    private final TenantWebsiteConfigService tenantWebsiteConfigService;
    private final BlogPostService blogPostService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('PERMISSION_support:tenants:read')")
    @Operation(summary = "Get support agent dashboard stats")
    public SupportDashboardResponse getDashboard(HttpServletRequest request) {
        return supportAdminService.getSupportDashboard(requireActorTenantId(request), requireActorUserId(request));
    }

    @GetMapping("/tenants")
    @PreAuthorize("hasAuthority('PERMISSION_support:tenants:read')")
    @Operation(summary = "List all tenants for support administration")
    public List<SupportTenantSummaryResponse> listTenants(HttpServletRequest request) {
        return supportAdminService.listTenants(requireActorTenantId(request), requireActorUserId(request));
    }

    @PatchMapping("/tenants/{tenantId}")
    @PreAuthorize("hasAuthority('PERMISSION_support:tenants:write')")
    @Operation(summary = "Update tenant data")
    public SupportTenantSummaryResponse updateTenant(@PathVariable UUID tenantId,
                                                     @Valid @RequestBody UpdateSupportTenantRequest body,
                                                     HttpServletRequest request) {
        return supportAdminService.updateTenant(requireActorTenantId(request), requireActorUserId(request), tenantId, body);
    }

    @PatchMapping("/tenants/{tenantId}/status")
    @PreAuthorize("hasAuthority('PERMISSION_support:tenants:write')")
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
    @PreAuthorize("hasAuthority('PERMISSION_support:tenant_users:read')")
    @Operation(summary = "List users from a tenant for support administration")
    public List<SupportUserSummaryResponse> listTenantUsers(@PathVariable UUID tenantId, HttpServletRequest request) {
        return supportAdminService.listTenantUsers(requireActorTenantId(request), requireActorUserId(request), tenantId);
    }

    @PatchMapping("/tenants/{tenantId}/users/{userId}")
    @PreAuthorize("hasAuthority('PERMISSION_support:tenant_users:write')")
    @Operation(summary = "Update tenant user data")
    public SupportUserSummaryResponse updateTenantUser(@PathVariable UUID tenantId,
                                                       @PathVariable UUID userId,
                                                       @Valid @RequestBody UpdateSupportUserRequest body,
                                                       HttpServletRequest request) {
        return supportAdminService.updateTenantUser(requireActorTenantId(request), requireActorUserId(request), tenantId, userId, body);
    }

    @PatchMapping("/tenants/{tenantId}/users/{userId}/status")
    @PreAuthorize("hasAuthority('PERMISSION_support:tenant_users:write')")
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
    @PreAuthorize("hasAuthority('PERMISSION_support:operators:read')")
    @Operation(summary = "List internal Rino operators")
    public List<SupportUserSummaryResponse> listOperators(HttpServletRequest request) {
        return supportAdminService.listOperators(requireActorTenantId(request), requireActorUserId(request));
    }

    @PatchMapping("/operators/{userId}/role")
    @PreAuthorize("hasAuthority('PERMISSION_support:operators:write')")
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

    @GetMapping("/operators/{userId}/permissions")
    @PreAuthorize("hasAuthority('PERMISSION_support:operators:read')")
    @Operation(summary = "List support permissions for an operator")
    public List<String> getOperatorPermissions(@PathVariable UUID userId) {
        return supportAdminService.getOperatorPermissions(userId);
    }

    @PutMapping("/operators/{userId}/permissions")
    @PreAuthorize("hasAuthority('PERMISSION_support:operators:write')")
    @Operation(summary = "Set support permissions for an operator (full replace)")
    public List<String> setOperatorPermissions(@PathVariable UUID userId,
                                               @RequestBody SetOperatorPermissionsRequest request) {
        return supportAdminService.setOperatorPermissions(userId, request.permissions());
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('PERMISSION_support:audit:read')")
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
    @PreAuthorize("hasAuthority('PERMISSION_support:health:read')")
    @Operation(summary = "Get tenant health summary")
    public SupportTenantHealthResponse getTenantHealth(@PathVariable UUID tenantId, HttpServletRequest request) {
        return supportAdminService.getTenantHealth(requireActorTenantId(request), requireActorUserId(request), tenantId);
    }

    @GetMapping("/tenants/{tenantId}/billing")
    @PreAuthorize("hasAuthority('PERMISSION_support:tenants:read')")
    @Operation(summary = "Get tenant billing plan, subscription and effective limits")
    public SupportTenantBillingResponse getTenantBilling(@PathVariable UUID tenantId, HttpServletRequest request) {
        return supportAdminService.getTenantBilling(requireActorTenantId(request), requireActorUserId(request), tenantId);
    }

    @PutMapping("/tenants/{tenantId}/billing")
    @PreAuthorize("hasAuthority('PERMISSION_support:tenants:write')")
    @Operation(summary = "Update tenant billing plan and limits")
    public SupportTenantBillingResponse updateTenantBilling(@PathVariable UUID tenantId,
                                                            @RequestBody UpdateSupportTenantBillingRequest request,
                                                            HttpServletRequest httpRequest) {
        return supportAdminService.updateTenantBilling(requireActorTenantId(httpRequest), requireActorUserId(httpRequest), tenantId, request);
    }

    @GetMapping("/tenants/{tenantId}/website-config")
    @PreAuthorize("hasAuthority('PERMISSION_support:tenants:read')")
    public TenantWebsiteConfigResponse getTenantWebsiteConfig(@PathVariable UUID tenantId) {
        return tenantWebsiteConfigService.getConfig(tenantId);
    }

    @PutMapping("/tenants/{tenantId}/website-config")
    @PreAuthorize("hasAuthority('PERMISSION_support:tenants:write')")
    public TenantWebsiteConfigResponse updateTenantWebsiteConfig(
            @PathVariable UUID tenantId,
            @RequestBody UpdateTenantWebsiteConfigRequest request) {
        return tenantWebsiteConfigService.updateConfig(tenantId, request);
    }

    @GetMapping("/tenants/{tenantId}/blog-posts")
    @PreAuthorize("hasAuthority('PERMISSION_support:tenants:read')")
    public Page<BlogPostResponse> listTenantBlogPosts(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return blogPostService.list(tenantId, page, size);
    }

    @PostMapping("/tenants/{tenantId}/blog-posts")
    @PreAuthorize("hasAuthority('PERMISSION_support:tenants:write')")
    public BlogPostResponse createTenantBlogPost(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateBlogPostRequest request) {
        return blogPostService.create(tenantId, request);
    }

    @PutMapping("/tenants/{tenantId}/blog-posts/{postId}")
    @PreAuthorize("hasAuthority('PERMISSION_support:tenants:write')")
    public BlogPostResponse updateTenantBlogPost(
            @PathVariable UUID tenantId,
            @PathVariable UUID postId,
            @Valid @RequestBody UpdateBlogPostRequest request) {
        return blogPostService.update(tenantId, postId, request);
    }

    @PatchMapping("/tenants/{tenantId}/blog-posts/{postId}/status")
    @PreAuthorize("hasAuthority('PERMISSION_support:tenants:write')")
    public BlogPostResponse updateTenantBlogPostStatus(
            @PathVariable UUID tenantId,
            @PathVariable UUID postId,
            @Valid @RequestBody UpdateBlogPostStatusRequest request) {
        return blogPostService.updateStatus(tenantId, postId, request.status());
    }

    @DeleteMapping("/tenants/{tenantId}/blog-posts/{postId}")
    @PreAuthorize("hasAuthority('PERMISSION_support:tenants:write')")
    public void deleteTenantBlogPost(
            @PathVariable UUID tenantId,
            @PathVariable UUID postId) {
        blogPostService.delete(tenantId, postId);
    }

    @PostMapping("/tenants/{tenantId}/users/{userId}/resend-invitation")
    @PreAuthorize("hasAuthority('PERMISSION_support:tenant_users:write')")
    @Operation(summary = "Resend a pending invitation for a tenant user")
    public SupportUserSummaryResponse resendInvitation(@PathVariable UUID tenantId,
                                                       @PathVariable UUID userId,
                                                       HttpServletRequest request) {
        return supportAdminService.resendInvitation(requireActorTenantId(request), requireActorUserId(request), tenantId, userId);
    }

    @PostMapping("/tenants/{tenantId}/users/{userId}/reset-access")
    @PreAuthorize("hasAuthority('PERMISSION_support:tenant_users:write')")
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
