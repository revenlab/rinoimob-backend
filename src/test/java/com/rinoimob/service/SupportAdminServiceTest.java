package com.rinoimob.service;

import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.enums.SystemRole;
import com.rinoimob.domain.entity.AuditLog;
import com.rinoimob.domain.entity.AutomationExecution;
import com.rinoimob.domain.entity.AutomationWorkflow;
import com.rinoimob.domain.entity.EmailSenderConfig;
import com.rinoimob.domain.repository.AuditLogRepository;
import com.rinoimob.domain.repository.AutomationExecutionRepository;
import com.rinoimob.domain.repository.AutomationWorkflowRepository;
import com.rinoimob.domain.repository.EmailSenderConfigRepository;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.TenantRoleRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.AuditService;
import com.rinoimob.service.UserManagementService;
import com.rinoimob.service.auth.TokenService;
import com.rinoimob.domain.enums.VerificationStatus;
import com.rinoimob.domain.enums.WorkflowExecutionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportAdminServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantRoleRepository tenantRoleRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private EmailSenderConfigRepository emailSenderConfigRepository;
    @Mock private AutomationExecutionRepository automationExecutionRepository;
    @Mock private AutomationWorkflowRepository automationWorkflowRepository;
    @Mock private UserManagementService userManagementService;
    @Mock private AuditService auditService;
    @Mock private TokenService tokenService;

    @Test
    void shouldListTenantsWithUserCounts() {
        UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID actorUserId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Tenant A");
        tenant.setSubdomain("tenant-a");
        tenant.setActive(true);
        tenant.setCreatedAt(LocalDateTime.now().minusDays(2));

        when(tenantRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(tenant));
        when(userRepository.countByTenantId(tenantId)).thenReturn(3L);

        SupportAdminService service = new SupportAdminService(
                tenantRepository, userRepository, tenantRoleRepository, auditLogRepository,
                emailSenderConfigRepository, automationExecutionRepository, automationWorkflowRepository,
                userManagementService,
                auditService, tokenService);

        var result = service.listTenants(tenantId, actorUserId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userCount()).isEqualTo(3L);
        verify(auditService).log(eq(tenantId.toString()), eq(actorUserId.toString()),
                eq("SUPPORT_VIEW_TENANTS"), eq("TENANT"), eq("ALL"), anyString());
    }

    @Test
    void shouldDeactivateTenantAndInvalidateTokens() {
        UUID tenantId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID actorUserId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Tenant B");
        tenant.setSubdomain("tenant-b");
        tenant.setActive(true);
        tenant.setCreatedAt(LocalDateTime.now());

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.countByTenantId(tenantId)).thenReturn(1L);

        SupportAdminService service = new SupportAdminService(
                tenantRepository, userRepository, tenantRoleRepository, auditLogRepository,
                emailSenderConfigRepository, automationExecutionRepository, automationWorkflowRepository,
                userManagementService,
                auditService, tokenService);

        var updated = service.setTenantActive(tenantId, actorUserId, tenantId, false);

        assertThat(updated.active()).isFalse();
        verify(tokenService).invalidateAllTenantTokens(tenantId);
        verify(auditService).log(eq(tenantId.toString()), eq(actorUserId.toString()),
                eq("TENANT_DEACTIVATED"), eq("TENANT"), eq(tenantId.toString()), contains("targetTenant=Tenant B"));
    }

    @Test
    void shouldDeactivateUserAndInvalidateUserTokens() {
        UUID tenantId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID userId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        UUID actorUserId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

        User user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setEmail("user@test.com");
        user.setFirstName("User");
        user.setLastName("Test");
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());

        when(userRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SupportAdminService service = new SupportAdminService(
                tenantRepository, userRepository, tenantRoleRepository, auditLogRepository,
                emailSenderConfigRepository, automationExecutionRepository, automationWorkflowRepository,
                userManagementService,
                auditService, tokenService);

        var updated = service.setUserActive(tenantId, actorUserId, tenantId, userId, false);

        assertThat(updated.active()).isFalse();
        verify(tokenService).invalidateUserTokens(userId);
        verify(auditService).log(eq(tenantId.toString()), eq(actorUserId.toString()),
                eq("USER_DEACTIVATED"), eq("USER"), eq(userId.toString()), contains("targetUser=User Test"));
    }

    @Test
    void shouldRejectNonInternalRoleWhenUpdatingOperatorRole() {
        UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID actorUserId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        SupportAdminService service = new SupportAdminService(
                tenantRepository, userRepository, tenantRoleRepository, auditLogRepository,
                emailSenderConfigRepository, automationExecutionRepository, automationWorkflowRepository,
                userManagementService,
                auditService, tokenService);

        assertThatThrownBy(() -> service.setOperatorRole(tenantId, actorUserId, userId, SystemRole.TENANT_OWNER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid internal role");
    }

    @Test
    void shouldFilterAuditLogsByTenantUserActionResourceAndPeriod() {
        UUID actorTenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID actorUserId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID tenantId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID userId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        LocalDateTime from = LocalDateTime.of(2026, 5, 20, 10, 15);
        LocalDateTime to = LocalDateTime.of(2026, 5, 20, 11, 30);

        AuditLog log = new AuditLog(tenantId.toString(), userId.toString(), "USER_DEACTIVATED",
                "USER", userId.toString(), "actorTenant=" + actorTenantId);
        when(auditLogRepository.findAll(any(Specification.class))).thenReturn(List.of(log));

        SupportAdminService service = new SupportAdminService(
                tenantRepository, userRepository, tenantRoleRepository, auditLogRepository,
                emailSenderConfigRepository, automationExecutionRepository, automationWorkflowRepository,
                userManagementService,
                auditService, tokenService);

        var result = service.listAuditLogs(actorTenantId, actorUserId, tenantId, userId,
                "USER_DEACTIVATED", "USER", from, to);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).action()).isEqualTo("USER_DEACTIVATED");
        verify(auditService).log(eq(actorTenantId.toString()), eq(actorUserId.toString()),
                eq("SUPPORT_VIEW_AUDIT"), eq("AUDIT_LOG"), eq(tenantId.toString()), contains("userId=" + userId));
    }

    @Test
    void shouldRejectAuditFiltersWhenStartIsAfterEnd() {
        UUID actorTenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID actorUserId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        LocalDateTime from = LocalDateTime.of(2026, 5, 20, 12, 0);
        LocalDateTime to = LocalDateTime.of(2026, 5, 20, 11, 0);

        SupportAdminService service = new SupportAdminService(
                tenantRepository, userRepository, tenantRoleRepository, auditLogRepository,
                emailSenderConfigRepository, automationExecutionRepository, automationWorkflowRepository,
                userManagementService,
                auditService, tokenService);

        assertThatThrownBy(() -> service.listAuditLogs(actorTenantId, actorUserId, null, null, null, null, from, to))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid audit period");
    }

    @Test
    void shouldBuildTenantHealthSummary() {
        UUID actorTenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID actorUserId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID tenantId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID workflowId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Tenant Health");
        tenant.setSubdomain("tenant-health");
        tenant.setActive(true);

        User pendingUser = new User();
        pendingUser.setId(UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"));
        pendingUser.setTenantId(tenantId);
        pendingUser.setEmail("pending@test.com");
        pendingUser.setFirstName("Pending");
        pendingUser.setLastName("User");
        pendingUser.setActive(true);
        pendingUser.setVerificationStatus(VerificationStatus.PENDING);

        User inactiveUser = new User();
        inactiveUser.setId(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
        inactiveUser.setTenantId(tenantId);
        inactiveUser.setEmail("inactive@test.com");
        inactiveUser.setFirstName("Inactive");
        inactiveUser.setLastName("User");
        inactiveUser.setActive(false);
        inactiveUser.setVerificationStatus(VerificationStatus.VERIFIED);

        EmailSenderConfig emailConfig = new EmailSenderConfig();
        emailConfig.setId(UUID.fromString("99999999-9999-9999-9999-999999999999"));
        emailConfig.setTenantId(tenantId);
        emailConfig.setDisplayName("Default SMTP");
        emailConfig.setFromEmail("support@example.com");
        emailConfig.setSmtpHost("smtp.example.com");
        emailConfig.setSmtpPort(587);
        emailConfig.setSmtpTls(true);
        emailConfig.setIsDefault(true);

        AutomationWorkflow workflow = new AutomationWorkflow();
        workflow.setId(workflowId);
        workflow.setTenantId(tenantId);
        workflow.setName("Lead follow-up");

        AutomationExecution execution = new AutomationExecution();
        execution.setId(UUID.fromString("abababab-abab-abab-abab-abababababab"));
        execution.setTenantId(tenantId);
        execution.setWorkflowId(workflowId);
        execution.setTriggerEvent("LEAD_CREATED");
        execution.setStatus(WorkflowExecutionStatus.FAILED);
        execution.setErrorMessage("SMTP timeout");
        execution.setCreatedAt(LocalDateTime.now().minusDays(1));

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(pendingUser, inactiveUser));
        when(emailSenderConfigRepository.findByTenantIdOrderByCreatedAtAsc(tenantId)).thenReturn(List.of(emailConfig));
        when(emailSenderConfigRepository.findByTenantIdAndIsDefaultTrue(tenantId)).thenReturn(Optional.of(emailConfig));
        when(automationExecutionRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, WorkflowExecutionStatus.FAILED))
                .thenReturn(List.of(execution));
        when(automationWorkflowRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(workflow));

        SupportAdminService service = new SupportAdminService(
                tenantRepository, userRepository, tenantRoleRepository, auditLogRepository,
                emailSenderConfigRepository, automationExecutionRepository, automationWorkflowRepository,
                userManagementService,
                auditService, tokenService);

        var health = service.getTenantHealth(actorTenantId, actorUserId, tenantId);

        assertThat(health.status()).isEqualTo("WARNING");
        assertThat(health.pendingInvites()).isEqualTo(1L);
        assertThat(health.inactiveUsers()).isEqualTo(1L);
        assertThat(health.failedExecutionsLast7Days()).isEqualTo(1L);
        assertThat(health.recentFailures()).hasSize(1);
        assertThat(health.recentFailures().get(0).workflowName()).isEqualTo("Lead follow-up");
        verify(auditService).log(eq(actorTenantId.toString()), eq(actorUserId.toString()),
                eq("SUPPORT_VIEW_TENANT_HEALTH"), eq("TENANT_HEALTH"), eq(tenantId.toString()), anyString());
    }

    @Test
    void shouldResendTenantInvitationAndLogAction() {
        UUID actorTenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID actorUserId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID tenantId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID userId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

        User user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setEmail("pending@test.com");
        user.setFirstName("Pending");
        user.setLastName("User");
        user.setActive(true);
        user.setVerificationStatus(VerificationStatus.PENDING);

        when(userRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(user));

        SupportAdminService service = new SupportAdminService(
                tenantRepository, userRepository, tenantRoleRepository, auditLogRepository,
                emailSenderConfigRepository, automationExecutionRepository, automationWorkflowRepository,
                userManagementService,
                auditService, tokenService);

        var updated = service.resendInvitation(actorTenantId, actorUserId, tenantId, userId);

        assertThat(updated.id()).isEqualTo(userId);
        verify(userManagementService).resendInvitation(tenantId, userId);
        verify(auditService).log(eq(actorTenantId.toString()), eq(actorUserId.toString()),
                eq("SUPPORT_RESEND_INVITATION"), eq("USER"), eq(userId.toString()), contains("targetUser=Pending User"));
    }
}
