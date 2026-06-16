package com.rinoimob.service.support;

import com.rinoimob.domain.dto.SupportTenantHealthFailureResponse;
import com.rinoimob.domain.dto.SupportTenantHealthResponse;
import com.rinoimob.domain.dto.SupportTenantSummaryResponse;
import com.rinoimob.domain.dto.UpdateSupportTenantRequest;
import com.rinoimob.domain.entity.AutomationExecution;
import com.rinoimob.domain.entity.EmailSenderConfig;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.enums.VerificationStatus;
import com.rinoimob.domain.enums.WorkflowExecutionStatus;
import com.rinoimob.domain.repository.AutomationExecutionRepository;
import com.rinoimob.domain.repository.AutomationWorkflowRepository;
import com.rinoimob.domain.repository.EmailSenderConfigRepository;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.core.AuditService;
import com.rinoimob.service.auth.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportAdminTenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final EmailSenderConfigRepository emailSenderConfigRepository;
    private final AutomationExecutionRepository automationExecutionRepository;
    private final AutomationWorkflowRepository automationWorkflowRepository;
    private final AuditService auditService;
    private final TokenService tokenService;
    private final SupportAdminAuditHelper auditHelper;
    private final SupportAdminMapperHelper mapperHelper;

    @Transactional
    public List<SupportTenantSummaryResponse> listTenants(UUID actorTenantId, UUID actorUserId) {
        auditHelper.logSupportView(actorTenantId, actorUserId, "SUPPORT_VIEW_TENANTS", "TENANT", "ALL", "Listed all tenants");
        return tenantRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(tenant -> new SupportTenantSummaryResponse(
                        tenant.getId(),
                        tenant.getName(),
                        tenant.getSubdomain(),
                        tenant.getActive(),
                        tenant.getCreatedAt(),
                        userRepository.countByTenantId(tenant.getId())
                ))
                .toList();
    }

    @Transactional
    public SupportTenantSummaryResponse updateTenant(UUID actorTenantId, UUID actorUserId, UUID tenantId, UpdateSupportTenantRequest request) {
        Tenant tenant = ensureTenantExists(tenantId);
        String normalizedName = request.name().trim();
        String normalizedSubdomain = request.subdomain().trim().toLowerCase();

        tenantRepository.findBySubdomain(normalizedSubdomain)
                .filter(existingTenant -> !existingTenant.getId().equals(tenantId))
                .ifPresent(existingTenant -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Subdomain is already taken");
                });

        String previousName = tenant.getName();
        String previousSubdomain = tenant.getSubdomain();

        tenant.setName(normalizedName);
        tenant.setSubdomain(normalizedSubdomain);
        Tenant saved = tenantRepository.save(tenant);

        auditService.log(tenantId.toString(), actorUserId != null ? actorUserId.toString() : null,
                "TENANT_UPDATED",
                "TENANT", tenantId.toString(),
                "actorTenant=" + actorTenantId
                        + ", actorUser=" + actorUserId
                        + ", previousName=" + previousName
                        + ", newName=" + saved.getName()
                        + ", previousSubdomain=" + previousSubdomain
                        + ", newSubdomain=" + saved.getSubdomain());

        return mapperHelper.toSupportTenantSummary(saved, userRepository);
    }

    @Transactional
    public SupportTenantSummaryResponse setTenantActive(UUID actorTenantId, UUID actorUserId, UUID tenantId, boolean active) {
        Tenant tenant = ensureTenantExists(tenantId);
        tenant.setActive(active);
        Tenant saved = tenantRepository.save(tenant);

        tokenService.invalidateAllTenantTokens(tenantId);
        auditService.log(tenantId.toString(), actorUserId != null ? actorUserId.toString() : null,
                active ? "TENANT_ACTIVATED" : "TENANT_DEACTIVATED",
                "TENANT", tenantId.toString(),
                "actorTenant=" + actorTenantId
                        + ", actorUser=" + actorUserId
                        + ", targetTenant=" + tenant.getName()
                        + " (" + tenant.getSubdomain() + ")"
                        + ", active=" + active);

        return mapperHelper.toSupportTenantSummary(saved, userRepository);
    }

    @Transactional
    public SupportTenantHealthResponse getTenantHealth(UUID actorTenantId, UUID actorUserId, UUID tenantId) {
        Tenant tenant = ensureTenantExists(tenantId);
        auditHelper.logSupportView(actorTenantId, actorUserId, "SUPPORT_VIEW_TENANT_HEALTH", "TENANT_HEALTH", tenantId.toString(),
                "Viewed tenant health for " + tenant.getName() + " (" + tenant.getSubdomain() + ")");

        List<User> users = userRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        List<com.rinoimob.domain.dto.SupportUserSummaryResponse> pendingInviteUsers = users.stream()
                .filter(user -> user.getVerificationStatus() == VerificationStatus.PENDING)
                .limit(5)
                .map(user -> mapperHelper.toSupportUserSummary(user, userRepository))
                .toList();
        List<com.rinoimob.domain.dto.SupportUserSummaryResponse> inactiveUsersSample = users.stream()
                .filter(user -> Boolean.FALSE.equals(user.getActive()))
                .limit(5)
                .map(user -> mapperHelper.toSupportUserSummary(user, userRepository))
                .toList();

        long totalUsers = users.size();
        long activeUsers = users.stream().filter(user -> Boolean.TRUE.equals(user.getActive())).count();
        long inactiveUsers = users.stream().filter(user -> Boolean.FALSE.equals(user.getActive())).count();
        long pendingInvites = users.stream().filter(user -> user.getVerificationStatus() == VerificationStatus.PENDING).count();

        List<EmailSenderConfig> emailConfigs = emailSenderConfigRepository.findByTenantIdOrderByCreatedAtAsc(tenantId);
        EmailSenderConfig defaultEmailSender = emailSenderConfigRepository.findByTenantIdAndIsDefaultTrue(tenantId).orElse(null);
        String emailSenderStatus = defaultEmailSender != null ? "OK" : emailConfigs.isEmpty() ? "MISSING" : "NO_DEFAULT";

        List<AutomationExecution> failedExecutions = automationExecutionRepository
                .findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, WorkflowExecutionStatus.FAILED);
        Map<UUID, String> workflowNames = new HashMap<>();
        automationWorkflowRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .forEach(workflow -> workflowNames.put(workflow.getId(), workflow.getName()));

        LocalDateTime recentWindow = LocalDateTime.now().minusDays(7);
        List<SupportTenantHealthFailureResponse> recentFailures = failedExecutions.stream()
                .filter(execution -> execution.getCreatedAt() != null && !execution.getCreatedAt().isBefore(recentWindow))
                .limit(5)
                .map(execution -> new SupportTenantHealthFailureResponse(
                        execution.getId(),
                        execution.getWorkflowId(),
                        workflowNames.getOrDefault(execution.getWorkflowId(), execution.getWorkflowId().toString()),
                        execution.getTriggerEvent(),
                        execution.getErrorMessage(),
                        execution.getCreatedAt()
                ))
                .toList();

        long failedExecutionsLast7Days = failedExecutions.stream()
                .filter(execution -> execution.getCreatedAt() != null && !execution.getCreatedAt().isBefore(recentWindow))
                .count();

        List<String> issues = new ArrayList<>();
        if (!Boolean.TRUE.equals(tenant.getActive())) {
            issues.add("Tenant desativado");
        }
        if (pendingInvites > 0) {
            issues.add(pendingInvites + " convites pendentes");
        }
        if (inactiveUsers > 0) {
            issues.add(inactiveUsers + " usuarios inativos");
        }
        if (!"OK".equals(emailSenderStatus)) {
            issues.add("Configuração de e-mail " + ("MISSING".equals(emailSenderStatus) ? "ausente" : "sem default"));
        }
        if (failedExecutionsLast7Days > 0) {
            issues.add(failedExecutionsLast7Days + " falhas recentes de automação");
        }

        String status = "OK";
        if (!Boolean.TRUE.equals(tenant.getActive()) || "MISSING".equals(emailSenderStatus)) {
            status = "CRITICAL";
        } else if (!issues.isEmpty()) {
            status = "WARNING";
        }

        return new SupportTenantHealthResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSubdomain(),
                tenant.getActive(),
                status,
                issues,
                totalUsers,
                activeUsers,
                inactiveUsers,
                pendingInvites,
                emailSenderStatus,
                emailConfigs.size(),
                defaultEmailSender != null ? defaultEmailSender.getDisplayName() : null,
                defaultEmailSender != null ? defaultEmailSender.getFromEmail() : null,
                failedExecutionsLast7Days,
                pendingInviteUsers,
                inactiveUsersSample,
                recentFailures
        );
    }

    private Tenant ensureTenantExists(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }
}
