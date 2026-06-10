package com.rinoimob.service.support;

import com.rinoimob.domain.dto.SupportAuditLogResponse;
import com.rinoimob.domain.dto.SupportDashboardResponse;
import com.rinoimob.domain.entity.AuditLog;
import com.rinoimob.domain.repository.AuditLogRepository;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.core.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportAdminDashboardService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final SupportAdminAuditHelper auditHelper;
    private final SupportAdminMapperHelper mapperHelper;

    @Transactional
    public SupportDashboardResponse getSupportDashboard(UUID actorTenantId, UUID actorUserId) {
        auditHelper.logSupportView(actorTenantId, actorUserId, "SUPPORT_VIEW_DASHBOARD", "SUPPORT_DASHBOARD", "SELF",
                "Viewed support dashboard");

        return new SupportDashboardResponse(
                tenantRepository.count(),
                tenantRepository.countByActive(true),
                tenantRepository.countByActive(false),
                userRepository.countNonInternalUsers(),
                auditLogRepository.findTop10ByUserIdOrderByCreatedAtDesc(actorUserId).stream()
                        .map(mapperHelper::toSupportAuditLogResponse)
                        .toList()
        );
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

        auditHelper.logSupportView(actorTenantId, actorUserId, "SUPPORT_VIEW_AUDIT", "AUDIT_LOG",
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
                .map(mapperHelper::toSupportAuditLogResponse)
                .toList();
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

    private String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String valueOrAll(Object value) {
        return value != null ? value.toString() : "ALL";
    }
}
