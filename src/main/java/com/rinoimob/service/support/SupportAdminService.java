package com.rinoimob.service.support;

import com.rinoimob.domain.dto.SupportAuditLogResponse;
import com.rinoimob.domain.dto.SupportDashboardResponse;
import com.rinoimob.domain.dto.SupportTenantHealthResponse;
import com.rinoimob.domain.dto.SupportTenantBillingResponse;
import com.rinoimob.domain.dto.SupportTenantSummaryResponse;
import com.rinoimob.domain.dto.SupportUserSummaryResponse;
import com.rinoimob.domain.dto.UpdateSupportTenantBillingRequest;
import com.rinoimob.domain.dto.UpdateSupportTenantRequest;
import com.rinoimob.domain.dto.UpdateSupportUserRequest;
import com.rinoimob.domain.enums.SystemRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Support Admin Service - Orchestrator
 * Delegates all operations to specialized sub-services:
 * - SupportAdminTenantService: tenant operations
 * - SupportAdminUserService: user operations
 * - SupportAdminOperatorService: operator/staff operations
 * - SupportAdminDashboardService: dashboard and audit logs
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupportAdminService {

    private final SupportAdminTenantService tenantService;
    private final SupportAdminUserService userService;
    private final SupportAdminOperatorService operatorService;
    private final SupportAdminDashboardService dashboardService;
    private final SupportAdminBillingService billingService;

    // ============ Tenant Operations ============

    @Transactional
    public List<SupportTenantSummaryResponse> listTenants(UUID actorTenantId, UUID actorUserId) {
        return tenantService.listTenants(actorTenantId, actorUserId);
    }

    @Transactional
    public SupportTenantSummaryResponse updateTenant(UUID actorTenantId, UUID actorUserId, UUID tenantId, UpdateSupportTenantRequest request) {
        return tenantService.updateTenant(actorTenantId, actorUserId, tenantId, request);
    }

    @Transactional
    public SupportTenantSummaryResponse setTenantActive(UUID actorTenantId, UUID actorUserId, UUID tenantId, boolean active) {
        return tenantService.setTenantActive(actorTenantId, actorUserId, tenantId, active);
    }

    @Transactional
    public SupportTenantHealthResponse getTenantHealth(UUID actorTenantId, UUID actorUserId, UUID tenantId) {
        return tenantService.getTenantHealth(actorTenantId, actorUserId, tenantId);
    }

    @Transactional
    public SupportTenantBillingResponse getTenantBilling(UUID actorTenantId, UUID actorUserId, UUID tenantId) {
        return billingService.getTenantBilling(actorTenantId, actorUserId, tenantId);
    }

    @Transactional
    public SupportTenantBillingResponse updateTenantBilling(UUID actorTenantId,
                                                            UUID actorUserId,
                                                            UUID tenantId,
                                                            UpdateSupportTenantBillingRequest request) {
        return billingService.updateTenantBilling(actorTenantId, actorUserId, tenantId, request);
    }

    // ============ Tenant User Operations ============

    @Transactional
    public List<SupportUserSummaryResponse> listTenantUsers(UUID actorTenantId, UUID actorUserId, UUID tenantId) {
        return userService.listTenantUsers(actorTenantId, actorUserId, tenantId);
    }

    @Transactional
    public SupportUserSummaryResponse updateTenantUser(UUID actorTenantId, UUID actorUserId, UUID tenantId, UUID userId, UpdateSupportUserRequest request) {
        return userService.updateTenantUser(actorTenantId, actorUserId, tenantId, userId, request);
    }

    @Transactional
    public SupportUserSummaryResponse setUserActive(UUID actorTenantId, UUID actorUserId, UUID tenantId, UUID userId, boolean active) {
        return userService.setUserActive(actorTenantId, actorUserId, tenantId, userId, active);
    }

    @Transactional
    public SupportUserSummaryResponse resendInvitation(UUID actorTenantId, UUID actorUserId, UUID tenantId, UUID userId) {
        return userService.resendInvitation(actorTenantId, actorUserId, tenantId, userId);
    }

    @Transactional
    public SupportUserSummaryResponse resetAccess(UUID actorTenantId, UUID actorUserId, UUID tenantId, UUID userId) {
        return userService.resetAccess(actorTenantId, actorUserId, tenantId, userId);
    }

    // ============ Operator/Staff Operations ============

    @Transactional
    public List<SupportUserSummaryResponse> listOperators(UUID actorTenantId, UUID actorUserId) {
        return operatorService.listOperators(actorTenantId, actorUserId);
    }

    @Transactional
    public List<String> getOperatorPermissions(UUID userId) {
        return operatorService.getOperatorPermissions(userId);
    }

    @Transactional
    public List<String> setOperatorPermissions(UUID userId, List<String> permissions) {
        return operatorService.setOperatorPermissions(userId, permissions);
    }

    @Transactional
    public SupportUserSummaryResponse setOperatorRole(UUID actorTenantId, UUID actorUserId, UUID userId, SystemRole systemRole) {
        return operatorService.setOperatorRole(actorTenantId, actorUserId, userId, systemRole);
    }

    // ============ Dashboard & Audit Operations ============

    @Transactional
    public SupportDashboardResponse getSupportDashboard(UUID actorTenantId, UUID actorUserId) {
        return dashboardService.getSupportDashboard(actorTenantId, actorUserId);
    }

    @Transactional
    public List<SupportAuditLogResponse> listAuditLogs(UUID actorTenantId,
                                                       UUID actorUserId,
                                                       UUID tenantId,
                                                       UUID userId,
                                                       String action,
                                                       String resource,
                                                       LocalDateTime startAt,
                                                       LocalDateTime endAt) {
        return dashboardService.listAuditLogs(actorTenantId, actorUserId, tenantId, userId, action, resource, startAt, endAt);
    }
}
