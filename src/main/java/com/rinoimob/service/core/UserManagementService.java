package com.rinoimob.service.core;

import com.rinoimob.domain.dto.InviteUserRequest;
import com.rinoimob.domain.dto.UserManagementResponse;
import com.rinoimob.domain.entity.GlobalCredential;
import com.rinoimob.domain.entity.TenantRole;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.entity.VerificationToken;
import com.rinoimob.domain.enums.SystemRole;
import com.rinoimob.domain.enums.VerificationStatus;
import com.rinoimob.domain.repository.GlobalCredentialRepository;
import com.rinoimob.domain.repository.TenantRoleRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.domain.repository.VerificationTokenRepository;
import com.rinoimob.exception.ForbiddenException;
import com.rinoimob.service.email.EmailService;
import com.rinoimob.service.billing.TenantQuotaEnforcementService;
import com.rinoimob.service.auth.PasswordEncoderService;
import com.rinoimob.service.auth.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class UserManagementService {
    private final UserRepository userRepository;
    private final TenantRoleRepository tenantRoleRepository;
    private final TokenService tokenService;
    private final TenantRoleService tenantRoleService;
    private final GlobalCredentialRepository globalCredentialRepository;
    private final PasswordEncoderService passwordEncoderService;
    private final VerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;
    private final TenantQuotaEnforcementService tenantQuotaEnforcementService;

    public UserManagementService(UserRepository userRepository,
                                  TenantRoleRepository tenantRoleRepository,
                                  TokenService tokenService,
                                  TenantRoleService tenantRoleService,
                                  GlobalCredentialRepository globalCredentialRepository,
                                  PasswordEncoderService passwordEncoderService,
                                  VerificationTokenRepository verificationTokenRepository,
                                  EmailService emailService,
                                  TenantQuotaEnforcementService tenantQuotaEnforcementService) {
        this.userRepository = userRepository;
        this.tenantRoleRepository = tenantRoleRepository;
        this.tokenService = tokenService;
        this.tenantRoleService = tenantRoleService;
        this.globalCredentialRepository = globalCredentialRepository;
        this.passwordEncoderService = passwordEncoderService;
        this.verificationTokenRepository = verificationTokenRepository;
        this.emailService = emailService;
        this.tenantQuotaEnforcementService = tenantQuotaEnforcementService;
    }

    public List<UserManagementResponse> listUsers(UUID tenantId) {
        return userRepository.findByTenantId(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UserManagementResponse inviteUser(UUID tenantId, InviteUserRequest request) {
        String normalizedEmail = request.email().toLowerCase();

        if (userRepository.existsByEmailAndTenantId(normalizedEmail, tenantId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered in this tenant");
        }

        tenantQuotaEnforcementService.assertCanCreateUser(tenantId);

        tenantRoleRepository.findByTenantIdAndId(tenantId, request.roleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));

        String tempPassword = UUID.randomUUID().toString();
        if (globalCredentialRepository.findByEmail(normalizedEmail).isEmpty()) {
            GlobalCredential credential = new GlobalCredential();
            credential.setEmail(normalizedEmail);
            credential.setPasswordHash(passwordEncoderService.encodePassword(tempPassword));
            globalCredentialRepository.save(credential);
        }

        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(normalizedEmail);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        user.setTenantRoleId(request.roleId());
        user.setVerificationStatus(VerificationStatus.PENDING);
        user.setActive(true);

        User saved = userRepository.save(user);

        String verificationToken = UUID.randomUUID().toString();
        VerificationToken token = new VerificationToken();
        token.setToken(verificationToken);
        token.setUserId(saved.getId());
        token.setTokenType("VERIFICATION");
        token.setExpiresAt(LocalDateTime.now().plusDays(7));
        verificationTokenRepository.save(token);
        emailService.sendInvitationEmail(saved.getEmail(), token.getToken(), saved.getFirstName());

        return toResponse(saved);
    }

    @Transactional
    public UserManagementResponse assignRole(UUID tenantId, UUID userId, UUID roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (SystemRole.TENANT_OWNER.equals(user.getSystemRole())) {
            throw new ForbiddenException("Cannot change role of TENANT_OWNER", "Proprietário do workspace não pode ter sua função alterada");
        }
        tenantRoleRepository.findByTenantIdAndId(tenantId, roleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        user.setTenantRoleId(roleId);
        User saved = userRepository.save(user);

        // Invalidate only THIS user's tokens — their role changed, so their JWT permissions are stale.
        // Use tenant-level invalidation only when role permissions themselves change (see TenantRoleService).
        tokenService.invalidateUserTokens(userId);

        return toResponse(saved);
    }

    @Transactional
    public void deactivateUser(UUID tenantId, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (SystemRole.TENANT_OWNER.equals(user.getSystemRole())) {
            throw new ForbiddenException("Cannot deactivate TENANT_OWNER", "Proprietário do workspace não pode ser desativado");
        }
        user.setActive(false);
        userRepository.save(user);
        
        // Invalidate tokens for this SPECIFIC USER only
        tokenService.invalidateUserTokens(userId);
    }

    @Transactional
    public void resendInvitation(UUID tenantId, UUID userId) {
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (VerificationStatus.VERIFIED.equals(user.getVerificationStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User has already accepted the invitation");
        }

        verificationTokenRepository.findByUserIdAndTokenType(userId, "VERIFICATION")
                .forEach(verificationTokenRepository::delete);

        VerificationToken token = new VerificationToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUserId(userId);
        token.setTokenType("VERIFICATION");
        token.setExpiresAt(LocalDateTime.now().plusDays(7));
        verificationTokenRepository.save(token);
        emailService.sendInvitationEmail(user.getEmail(), token.getToken(), user.getFirstName());
    }

    private UserManagementResponse toResponse(User user) {
        String roleName = null;
        if (user.getTenantRoleId() != null) {
            roleName = tenantRoleRepository.findById(user.getTenantRoleId())
                    .map(TenantRole::getName)
                    .orElse(null);
        }
        return new UserManagementResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                Boolean.TRUE.equals(user.getActive()),
                user.getSystemRole(),
                user.getTenantRoleId(),
                roleName,
                user.getCreatedAt(),
                user.getVerificationStatus()
        );
    }
}
