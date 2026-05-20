package com.rinoimob.service;

import com.rinoimob.domain.dto.SupportTenantSummaryResponse;
import com.rinoimob.domain.dto.SupportAuditLogResponse;
import com.rinoimob.domain.dto.SupportTenantHealthFailureResponse;
import com.rinoimob.domain.dto.SupportTenantHealthResponse;
import com.rinoimob.domain.dto.SupportUserSummaryResponse;
import com.rinoimob.domain.entity.AuditLog;
import com.rinoimob.domain.entity.AutomationExecution;
import com.rinoimob.domain.entity.EmailSenderConfig;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.entity.TenantRole;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.enums.SystemRole;
import com.rinoimob.domain.enums.VerificationStatus;
import com.rinoimob.domain.enums.WorkflowExecutionStatus;
import com.rinoimob.domain.repository.AuditLogRepository;
import com.rinoimob.domain.repository.AutomationExecutionRepository;
import com.rinoimob.domain.repository.AutomationWorkflowRepository;
import com.rinoimob.domain.repository.EmailSenderConfigRepository;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.TenantRoleRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.AuditService;
import com.rinoimob.service.auth.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SupportAdminService {

    private static final List<SystemRole> INTERNAL_ROLES =
            List.of(SystemRole.TENANT_ADMIN, SystemRole.SUPPORT_MANAGER, SystemRole.SUPPORT_AGENT);

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TenantRoleRepository tenantRoleRepository;
    private final AuditLogRepository auditLogRepository;
    private final EmailSenderConfigRepository emailSenderConfigRepository;
    private final AutomationExecutionRepository automationExecutionRepository;
    private final AutomationWorkflowRepository automationWorkflowRepository;
    private final UserManagementService userManagementService;
    private final AuditService auditService;
    private final TokenService tokenService;

    public SupportAdminService(TenantRepository tenantRepository,
                               UserRepository userRepository,
                               TenantRoleRepository tenantRoleRepository,
                               AuditLogRepository auditLogRepository,
                               EmailSenderConfigRepository emailSenderConfigRepository,
                               AutomationExecutionRepository automationExecutionRepository,
                               AutomationWorkflowRepository automationWorkflowRepository,
                               UserManagementService userManagementService,
                               AuditService auditService,
                               TokenService tokenService) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.tenantRoleRepository = tenantRoleRepository;
        this.auditLogRepository = auditLogRepository;
        this.emailSenderConfigRepository = emailSenderConfigRepository;
        this.automationExecutionRepository = automationExecutionRepository;
        this.automationWorkflowRepository = automationWorkflowRepository;
        this.userManagementService = userManagementService;
        this.auditService = auditService;
        this.tokenService = tokenService;
    }

    @Transactional
    public List<SupportTenantSummaryResponse> listTenants(UUID actorTenantId, UUID actorUserId) {
        logSupportView(actorTenantId, actorUserId, "SUPPORT_VIEW_TENANTS", "TENANT", "ALL", "Listed all tenants");
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
    public List<SupportUserSummaryResponse> listTenantUsers(UUID actorTenantId, UUID actorUserId, UUID tenantId) {
        ensureTenantExists(tenantId);
        logSupportView(actorTenantId, actorUserId, "SUPPORT_VIEW_TENANT_USERS", "TENANT", tenantId.toString(),
                "Listed users for tenant " + tenantId);

        return userRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toSupportUserSummary)
                .toList();
    }

    @Transactional
    public List<SupportUserSummaryResponse> listOperators(UUID actorTenantId, UUID actorUserId) {
        logSupportView(actorTenantId, actorUserId, "SUPPORT_VIEW_OPERATORS", "SUPPORT_OPERATOR", "ALL",
                "Listed internal operators");
        return userRepository.findBySystemRoleInOrderByCreatedAtDesc(INTERNAL_ROLES).stream()
                .map(this::toSupportUserSummary)
                .toList();
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

        return new SupportTenantSummaryResponse(
                saved.getId(),
                saved.getName(),
                saved.getSubdomain(),
                saved.getActive(),
                saved.getCreatedAt(),
                userRepository.countByTenantId(saved.getId())
        );
    }

    @Transactional
    public SupportUserSummaryResponse setUserActive(UUID actorTenantId, UUID actorUserId, UUID tenantId, UUID userId, boolean active) {
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        user.setActive(active);
        User saved = userRepository.save(user);
        tokenService.invalidateUserTokens(saved.getId());
        auditService.log(tenantId.toString(), actorUserId != null ? actorUserId.toString() : null,
                active ? "USER_ACTIVATED" : "USER_DEACTIVATED",
                "USER", userId.toString(),
                "actorTenant=" + actorTenantId
                        + ", actorUser=" + actorUserId
                        + ", targetUser=" + user.getFirstName() + " " + user.getLastName()
                        + " <" + user.getEmail() + ">"
                        + ", targetTenant=" + tenantId
                        + ", active=" + active);

        return toSupportUserSummary(saved);
    }

    @Transactional
    public SupportUserSummaryResponse setOperatorRole(UUID actorTenantId, UUID actorUserId, UUID userId, SystemRole systemRole) {
        if (systemRole == null || !systemRole.isInternalStaff()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid internal role");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        SystemRole previousRole = user.getSystemRole();
        user.setSystemRole(systemRole);
        User saved = userRepository.save(user);
        tokenService.invalidateUserTokens(saved.getId());

        auditService.log(actorTenantId.toString(), actorUserId.toString(),
                "SUPPORT_ROLE_CHANGED",
                "SUPPORT_OPERATOR", userId.toString(),
                "actorTenant=" + actorTenantId
                        + ", actorUser=" + actorUserId
                        + ", targetUser=" + user.getFirstName() + " " + user.getLastName()
                        + " <" + user.getEmail() + ">"
                        + ", previousRole=" + previousRole
                        + ", newRole=" + systemRole.name());

        return toSupportUserSummary(saved);
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
        LocalDateTime normalizedEndAt = normalizeEndDateTime(endAt);
        validateAuditFilterRange(startAt, normalizedEndAt);

        String normalizedAction = normalizeFilter(action);
        String normalizedResource = normalizeFilter(resource);
        String tenantIdFilter = tenantId != null ? tenantId.toString() : null;
        String userIdFilter = userId != null ? userId.toString() : null;

        logSupportView(actorTenantId, actorUserId, "SUPPORT_VIEW_AUDIT", "AUDIT_LOG",
                tenantIdFilter != null ? tenantIdFilter : "ALL",
                "Viewed audit logs with tenantId=" + valueOrAll(tenantIdFilter)
                        + ", userId=" + valueOrAll(userIdFilter)
                        + ", action=" + valueOrAll(normalizedAction)
                        + ", resource=" + valueOrAll(normalizedResource)
                        + ", startAt=" + valueOrAll(startAt)
                        + ", endAt=" + valueOrAll(normalizedEndAt));

        List<AuditLog> logs = auditLogRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tenantIdFilter != null) predicates.add(cb.equal(root.get("tenantId"), tenantIdFilter));
            if (userIdFilter != null) predicates.add(cb.equal(root.get("userId"), userIdFilter));
            if (normalizedAction != null) predicates.add(cb.equal(root.get("action"), normalizedAction));
            if (normalizedResource != null) predicates.add(cb.equal(root.get("resource"), normalizedResource));
            if (startAt != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startAt));
            if (normalizedEndAt != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), normalizedEndAt));
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });

        return logs.stream()
                .map(this::toSupportAuditLogResponse)
                .toList();
    }

    @Transactional
    public SupportTenantHealthResponse getTenantHealth(UUID actorTenantId, UUID actorUserId, UUID tenantId) {
        Tenant tenant = ensureTenantExists(tenantId);
        logSupportView(actorTenantId, actorUserId, "SUPPORT_VIEW_TENANT_HEALTH", "TENANT_HEALTH", tenantId.toString(),
                "Viewed tenant health for " + tenant.getName() + " (" + tenant.getSubdomain() + ")");

        List<User> users = userRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        List<SupportUserSummaryResponse> pendingInviteUsers = users.stream()
                .filter(user -> user.getVerificationStatus() == VerificationStatus.PENDING)
                .limit(5)
                .map(this::toSupportUserSummary)
                .toList();
        List<SupportUserSummaryResponse> inactiveUsersSample = users.stream()
                .filter(user -> Boolean.FALSE.equals(user.getActive()))
                .limit(5)
                .map(this::toSupportUserSummary)
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

        List<String> issues = new java.util.ArrayList<>();
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

    @Transactional
    public SupportUserSummaryResponse resendInvitation(UUID actorTenantId, UUID actorUserId, UUID tenantId, UUID userId) {
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        userManagementService.resendInvitation(tenantId, userId);
        auditService.log(actorTenantId.toString(), actorUserId.toString(),
                "SUPPORT_RESEND_INVITATION", "USER", userId.toString(),
                "actorTenant=" + actorTenantId
                        + ", actorUser=" + actorUserId
                        + ", targetUser=" + user.getFirstName() + " " + user.getLastName()
                        + " <" + user.getEmail() + ">"
                        + ", targetTenant=" + tenantId);

        return toSupportUserSummary(user);
    }

    @Transactional
    public SupportUserSummaryResponse resetAccess(UUID actorTenantId, UUID actorUserId, UUID tenantId, UUID userId) {
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (Boolean.FALSE.equals(user.getActive())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is inactive and cannot be reset");
        }

        user.setForcePasswordReset(true);
        userRepository.save(user);

        tokenService.invalidateUserTokens(userId);

        auditService.log(actorTenantId.toString(), actorUserId.toString(),
                "SUPPORT_RESET_ACCESS", "USER", userId.toString(),
                "actorTenant=" + actorTenantId
                        + ", actorUser=" + actorUserId
                        + ", targetUser=" + user.getFirstName() + " " + user.getLastName()
                        + " <" + user.getEmail() + ">"
                        + ", targetTenant=" + tenantId);

        return toSupportUserSummary(user);
    }

    private Tenant ensureTenantExists(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private SupportUserSummaryResponse toSupportUserSummary(User user) {
        String tenantRoleName = null;
        if (user.getTenantRoleId() != null) {
            tenantRoleName = tenantRoleRepository.findById(user.getTenantRoleId())
                    .map(TenantRole::getName)
                    .orElse(null);
        }

        return new SupportUserSummaryResponse(
                user.getId(),
                user.getTenantId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getActive(),
                user.getSystemRole(),
                user.getTenantRoleId(),
                tenantRoleName,
                user.getVerificationStatus(),
                user.getCreatedAt()
        );
    }

    private SupportAuditLogResponse toSupportAuditLogResponse(AuditLog log) {
        String actorTenantName = resolveTenantName(log.getTenantId());
        String actorUserName = resolveUserName(log.getUserId());
        String targetLabel = resolveTargetLabel(log.getResource(), log.getResourceId());

        return new SupportAuditLogResponse(
                log.getId(),
                log.getTenantId(),
                actorTenantName,
                log.getUserId(),
                actorUserName,
                log.getAction(),
                log.getResource(),
                log.getResourceId(),
                targetLabel,
                log.getDetails(),
                log.getCreatedAt()
        );
    }

    private void logSupportView(UUID actorTenantId, UUID actorUserId, String action, String resource, String resourceId, String details) {
        if (actorTenantId == null || actorUserId == null) {
            return;
        }

        auditService.log(actorTenantId.toString(), actorUserId.toString(), action, resource, resourceId, details);
    }

    private String resolveTenantName(String tenantId) {
        UUID id = parseUuid(tenantId);
        if (id == null) {
            return tenantId;
        }

        return tenantRepository.findById(id)
                .map(Tenant::getName)
                .orElse(tenantId);
    }

    private String resolveUserName(String userId) {
        UUID id = parseUuid(userId);
        if (id == null) {
            return userId;
        }

        return userRepository.findById(id)
                .map(user -> user.getFirstName() + " " + user.getLastName())
                .orElse(userId);
    }

    private String resolveTargetLabel(String resource, String resourceId) {
        UUID id = parseUuid(resourceId);
        if (id == null) {
            return resourceId != null ? resourceId : "—";
        }

        if ("TENANT".equals(resource) || "AUDIT_LOG".equals(resource)) {
            return tenantRepository.findById(id)
                    .map(tenant -> tenant.getName() + " @" + tenant.getSubdomain())
                    .orElse(resourceId);
        }

        if ("USER".equals(resource) || "SUPPORT_OPERATOR".equals(resource)) {
            return userRepository.findById(id)
                    .map(user -> user.getFirstName() + " " + user.getLastName() + " <" + user.getEmail() + ">")
                    .orElse(resourceId);
        }

        return resourceId;
    }

    private String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private LocalDateTime normalizeEndDateTime(LocalDateTime endAt) {
        if (endAt == null) {
            return null;
        }

        return endAt.withSecond(59).withNano(999_999_999);
    }

    private void validateAuditFilterRange(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt != null && endAt != null && startAt.isAfter(endAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid audit period");
        }
    }

    private String valueOrAll(Object value) {
        return value != null ? value.toString() : "ALL";
    }

    private UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
