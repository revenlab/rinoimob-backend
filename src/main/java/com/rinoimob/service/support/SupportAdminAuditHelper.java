package com.rinoimob.service.support;

import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.core.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SupportAdminAuditHelper {

    private final AuditService auditService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    public void logSupportView(UUID actorTenantId, UUID actorUserId, String action, String resource, String resourceId, String details) {
        if (actorTenantId == null || actorUserId == null) {
            return;
        }

        auditService.log(actorTenantId.toString(), actorUserId.toString(), action, resource, resourceId, details);
    }

    public String resolveTenantName(String tenantId) {
        UUID id = parseUuid(tenantId);
        if (id == null) {
            return tenantId;
        }

        return tenantRepository.findById(id)
                .map(Tenant::getName)
                .orElse(tenantId);
    }

    public String resolveUserName(String userId) {
        UUID id = parseUuid(userId);
        if (id == null) {
            return userId;
        }

        return userRepository.findById(id)
                .map(user -> user.getFirstName() + " " + user.getLastName())
                .orElse(userId);
    }

    public String resolveTargetLabel(String resource, String resourceId) {
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
