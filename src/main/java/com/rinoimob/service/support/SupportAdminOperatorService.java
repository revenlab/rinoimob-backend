package com.rinoimob.service.support;

import com.rinoimob.domain.dto.SupportUserSummaryResponse;
import com.rinoimob.domain.entity.SupportUserPermission;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.enums.SupportPermission;
import com.rinoimob.domain.enums.SystemRole;
import com.rinoimob.domain.repository.SupportUserPermissionRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.core.AuditService;
import com.rinoimob.service.auth.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportAdminOperatorService {

    private static final List<SystemRole> INTERNAL_ROLES =
            List.of(SystemRole.TENANT_ADMIN, SystemRole.SUPPORT_MANAGER, SystemRole.SUPPORT_AGENT);

    private final UserRepository userRepository;
    private final SupportUserPermissionRepository supportUserPermissionRepository;
    private final AuditService auditService;
    private final TokenService tokenService;
    private final SupportAdminAuditHelper auditHelper;
    private final SupportAdminMapperHelper mapperHelper;

    @Transactional
    public List<SupportUserSummaryResponse> listOperators(UUID actorTenantId, UUID actorUserId) {
        auditHelper.logSupportView(actorTenantId, actorUserId, "SUPPORT_VIEW_OPERATORS", "SUPPORT_OPERATOR", "ALL",
                "Listed internal operators");
        return userRepository.findBySystemRoleInOrderByCreatedAtDesc(INTERNAL_ROLES).stream()
                .map(user -> mapperHelper.toSupportUserSummary(user, userRepository))
                .toList();
    }

    @Transactional
    public List<String> getOperatorPermissions(UUID userId) {
        ensureInternalOperatorExists(userId);
        return supportUserPermissionRepository.findPermissionValuesByUserId(userId);
    }

    @Transactional
    public List<String> setOperatorPermissions(UUID userId, List<String> permissions) {
        ensureInternalOperatorExists(userId);
        List<String> normalizedPermissions = normalizeSupportPermissions(permissions);

        supportUserPermissionRepository.deleteByUserId(userId);
        if (!normalizedPermissions.isEmpty()) {
            List<SupportUserPermission> supportPermissions = new ArrayList<>();
            for (String permission : normalizedPermissions) {
                SupportUserPermission supportPermission = new SupportUserPermission();
                supportPermission.setUserId(userId);
                supportPermission.setPermission(permission);
                supportPermissions.add(supportPermission);
            }
            supportUserPermissionRepository.saveAll(supportPermissions);
        }

        tokenService.invalidateUserTokens(userId);
        return supportUserPermissionRepository.findPermissionValuesByUserId(userId);
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

        return mapperHelper.toSupportUserSummary(saved, userRepository);
    }

    private User ensureInternalOperatorExists(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getSystemRole() == null || !user.getSystemRole().isInternalStaff()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not an internal operator");
        }
        return user;
    }

    private List<String> normalizeSupportPermissions(List<String> permissions) {
        if (permissions == null) {
            return List.of();
        }

        LinkedHashSet<String> normalizedPermissions = new LinkedHashSet<>();
        for (String permission : permissions) {
            normalizedPermissions.add(SupportPermission.fromValue(permission).getValue());
        }
        return new ArrayList<>(normalizedPermissions);
    }
}
