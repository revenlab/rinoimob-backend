package com.rinoimob.service.support;

import com.rinoimob.domain.dto.SupportAuditLogResponse;
import com.rinoimob.domain.dto.SupportTenantSummaryResponse;
import com.rinoimob.domain.dto.SupportUserSummaryResponse;
import com.rinoimob.domain.entity.AuditLog;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.entity.TenantRole;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.TenantRoleRepository;
import com.rinoimob.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SupportAdminMapperHelper {

    private final TenantRepository tenantRepository;
    private final TenantRoleRepository tenantRoleRepository;
    private final UserRepository userRepository;
    private final SupportAdminAuditHelper auditHelper;

    public SupportTenantSummaryResponse toSupportTenantSummary(Tenant tenant, UserRepository userRepository) {
        return new SupportTenantSummaryResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSubdomain(),
                tenant.getActive(),
                tenant.getCreatedAt(),
                userRepository.countByTenantId(tenant.getId())
        );
    }

    public SupportUserSummaryResponse toSupportUserSummary(User user, UserRepository userRepository) {
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

    public SupportAuditLogResponse toSupportAuditLogResponse(AuditLog log) {
        String actorTenantName = auditHelper.resolveTenantName(log.getTenantId());
        String actorUserName = auditHelper.resolveUserName(log.getUserId());
        String targetLabel = auditHelper.resolveTargetLabel(log.getResource(), log.getResourceId());

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
}
