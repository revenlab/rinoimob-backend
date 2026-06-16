package com.rinoimob.service.support;

import com.rinoimob.domain.dto.SupportUserSummaryResponse;
import com.rinoimob.domain.dto.UpdateSupportUserRequest;
import com.rinoimob.domain.entity.GlobalCredential;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.repository.GlobalCredentialRepository;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.core.AuditService;
import com.rinoimob.service.core.UserManagementService;
import com.rinoimob.service.auth.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportAdminUserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final GlobalCredentialRepository globalCredentialRepository;
    private final UserManagementService userManagementService;
    private final AuditService auditService;
    private final TokenService tokenService;
    private final SupportAdminAuditHelper auditHelper;
    private final SupportAdminMapperHelper mapperHelper;

    @Transactional
    public List<SupportUserSummaryResponse> listTenantUsers(UUID actorTenantId, UUID actorUserId, UUID tenantId) {
        ensureTenantExists(tenantId);
        auditHelper.logSupportView(actorTenantId, actorUserId, "SUPPORT_VIEW_TENANT_USERS", "TENANT", tenantId.toString(),
                "Listed users for tenant " + tenantId);

        return userRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(user -> mapperHelper.toSupportUserSummary(user, userRepository))
                .toList();
    }

    @Transactional
    public SupportUserSummaryResponse updateTenantUser(UUID actorTenantId, UUID actorUserId, UUID tenantId, UUID userId, UpdateSupportUserRequest request) {
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String normalizedFirstName = request.firstName().trim();
        String normalizedLastName = request.lastName().trim();
        String normalizedPhone = normalizeOptionalValue(request.phone());
        String normalizedEmail = request.email().trim().toLowerCase();

        userRepository.findByTenantIdAndEmail(tenantId, normalizedEmail)
                .filter(existingUser -> !existingUser.getId().equals(userId))
                .ifPresent(existingUser -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered in this tenant");
                });

        String previousFirstName = user.getFirstName();
        String previousLastName = user.getLastName();
        String previousPhone = user.getPhone();
        String previousEmail = user.getEmail();
        boolean emailChanged = !previousEmail.equals(normalizedEmail);

        GlobalCredential previousCredential = null;
        if (emailChanged) {
            previousCredential = globalCredentialRepository.findByEmail(previousEmail)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Global credential not found for user"));

            if (globalCredentialRepository.findByEmail(normalizedEmail).isEmpty()) {
                GlobalCredential newCredential = new GlobalCredential();
                newCredential.setEmail(normalizedEmail);
                newCredential.setPasswordHash(previousCredential.getPasswordHash());
                globalCredentialRepository.save(newCredential);
            }
        }

        user.setFirstName(normalizedFirstName);
        user.setLastName(normalizedLastName);
        user.setPhone(normalizedPhone);
        user.setEmail(normalizedEmail);
        User saved = userRepository.save(user);

        if (emailChanged) {
            tokenService.invalidateUserTokens(saved.getId());
            if (userRepository.findAllByEmail(previousEmail).isEmpty()) {
                globalCredentialRepository.deleteById(previousEmail);
            }
        }

        auditService.log(tenantId.toString(), actorUserId != null ? actorUserId.toString() : null,
                "USER_UPDATED",
                "USER", userId.toString(),
                "actorTenant=" + actorTenantId
                        + ", actorUser=" + actorUserId
                        + ", targetTenant=" + tenantId
                        + ", previousFirstName=" + valueOrAll(previousFirstName)
                        + ", newFirstName=" + saved.getFirstName()
                        + ", previousLastName=" + valueOrAll(previousLastName)
                        + ", newLastName=" + saved.getLastName()
                        + ", previousPhone=" + valueOrAll(previousPhone)
                        + ", newPhone=" + valueOrAll(saved.getPhone())
                        + ", previousEmail=" + previousEmail
                        + ", newEmail=" + saved.getEmail());

        return mapperHelper.toSupportUserSummary(saved, userRepository);
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

        return mapperHelper.toSupportUserSummary(saved, userRepository);
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

        return mapperHelper.toSupportUserSummary(user, userRepository);
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

        return mapperHelper.toSupportUserSummary(user, userRepository);
    }

    private void ensureTenantExists(UUID tenantId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private String normalizeOptionalValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String valueOrAll(Object value) {
        return value != null ? value.toString() : "ALL";
    }
}
