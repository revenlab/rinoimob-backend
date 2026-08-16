package com.rinoimob.service.auth;

import com.rinoimob.config.security.JwtTokenProvider;
import com.rinoimob.domain.dto.IdentifyRequest;
import com.rinoimob.domain.dto.IdentifyResponse;
import com.rinoimob.domain.dto.LoginRequest;
import com.rinoimob.domain.dto.LoginResponse;
import com.rinoimob.domain.dto.MeResponse;
import com.rinoimob.domain.dto.RegisterRequest;
import com.rinoimob.domain.dto.SelectTenantRequest;
import com.rinoimob.domain.dto.TenantRegistrationRequest;
import com.rinoimob.domain.dto.TenantSummary;
import com.rinoimob.domain.dto.UserDto;
import com.rinoimob.domain.entity.GlobalCredential;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.entity.VerificationToken;
import com.rinoimob.domain.enums.SystemRole;
import com.rinoimob.domain.enums.VerificationStatus;
import com.rinoimob.domain.dto.OnboardingSummaryResponse;
import com.rinoimob.domain.repository.GlobalCredentialRepository;
import com.rinoimob.domain.repository.SupportUserPermissionRepository;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.domain.repository.VerificationTokenRepository;
import com.rinoimob.context.TenantContext;
import com.rinoimob.service.billing.TenantQuotaEnforcementService;
import com.rinoimob.service.core.TenantRoleService;
import com.rinoimob.service.billing.TenantBillingOnboardingService;
import com.rinoimob.service.email.EmailService;
import com.rinoimob.service.imoveis.PropertyTypeService;
import com.rinoimob.service.onboarding.UserOnboardingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final GlobalCredentialRepository globalCredentialRepository;
    private final VerificationTokenRepository tokenRepository;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoderService passwordEncoderService;
    private final EmailService emailService;
    private final TenantRoleService tenantRoleService;
    private final TenantBillingOnboardingService tenantBillingOnboardingService;
    private final PropertyTypeService propertyTypeService;
    private final TenantQuotaEnforcementService tenantQuotaEnforcementService;
    private final TokenService tokenService;
    private final SupportUserPermissionRepository supportUserPermissionRepository;
    private final UserOnboardingService userOnboardingService;

    @Value("${app.verification-token-expiration:86400}")
    private long verificationTokenExpiration;

    @Value("${app.reset-token-expiration:3600}")
    private long resetTokenExpiration;

    public AuthService(UserRepository userRepository,
                       TenantRepository tenantRepository,
                       GlobalCredentialRepository globalCredentialRepository,
                       VerificationTokenRepository tokenRepository,
                       JwtTokenProvider tokenProvider,
                       PasswordEncoderService passwordEncoderService,
                       EmailService emailService,
                       TenantRoleService tenantRoleService,
                       TenantBillingOnboardingService tenantBillingOnboardingService,
                       PropertyTypeService propertyTypeService,
                       TenantQuotaEnforcementService tenantQuotaEnforcementService,
                       TokenService tokenService,
                       SupportUserPermissionRepository supportUserPermissionRepository,
                       UserOnboardingService userOnboardingService) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.globalCredentialRepository = globalCredentialRepository;
        this.tokenRepository = tokenRepository;
        this.tokenProvider = tokenProvider;
        this.passwordEncoderService = passwordEncoderService;
        this.emailService = emailService;
        this.tenantRoleService = tenantRoleService;
        this.tenantBillingOnboardingService = tenantBillingOnboardingService;
        this.propertyTypeService = propertyTypeService;
        this.tenantQuotaEnforcementService = tenantQuotaEnforcementService;
        this.tokenService = tokenService;
        this.supportUserPermissionRepository = supportUserPermissionRepository;
        this.userOnboardingService = userOnboardingService;
    }

    @Transactional
    public void signup(TenantRegistrationRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        String normalizedSubdomain = request.subdomain().toLowerCase();
        String normalizedEmail = request.email().toLowerCase();

        if (tenantRepository.findBySubdomain(normalizedSubdomain).isPresent()) {
            throw new IllegalArgumentException("Subdomain is already taken");
        }

        if (tenantRepository.findByName(request.tenantName()).isPresent()) {
            throw new IllegalArgumentException("Tenant name is already taken");
        }

        Tenant tenant = new Tenant();
        tenant.setName(request.tenantName());
        tenant.setSubdomain(normalizedSubdomain);
        Tenant savedTenant = tenantRepository.save(tenant);

        if (globalCredentialRepository.findByEmail(normalizedEmail).isEmpty()) {
            GlobalCredential credential = new GlobalCredential();
            credential.setEmail(normalizedEmail);
            credential.setPasswordHash(passwordEncoderService.encodePassword(request.password()));
            globalCredentialRepository.save(credential);
        }

        User user = new User();
        user.setTenantId(savedTenant.getId());
        user.setEmail(normalizedEmail);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setSystemRole(SystemRole.TENANT_OWNER);
        user.setVerificationStatus(VerificationStatus.PENDING);
        user.setActive(true);

        User savedUser = userRepository.save(user);

        tenantRoleService.seedDefaultRoles(savedTenant.getId());
        tenantBillingOnboardingService.provisionDefaultFreePlan(savedTenant.getId());
        propertyTypeService.provisionDefaults(savedTenant.getId());

        String verificationToken = UUID.randomUUID().toString();
        VerificationToken token = new VerificationToken();
        token.setToken(verificationToken);
        token.setUserId(savedUser.getId());
        token.setTokenType("VERIFICATION");
        token.setExpiresAt(LocalDateTime.now().plusSeconds(verificationTokenExpiration));

        tokenRepository.save(token);
        emailService.sendVerificationEmail(savedUser.getEmail(), verificationToken);

        log.info("New tenant '{}' created with owner: {}", savedTenant.getSubdomain(), savedUser.getEmail());
    }

    @Transactional
    public void register(RegisterRequest request, UUID tenantId) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        String normalizedEmail = request.email().toLowerCase();

        if (userRepository.existsByEmailAndTenantId(normalizedEmail, tenantId)) {
            throw new IllegalArgumentException("Email already registered");
        }

        tenantQuotaEnforcementService.assertCanCreateUser(tenantId);

        if (globalCredentialRepository.findByEmail(normalizedEmail).isEmpty()) {
            GlobalCredential credential = new GlobalCredential();
            credential.setEmail(normalizedEmail);
            credential.setPasswordHash(passwordEncoderService.encodePassword(request.password()));
            globalCredentialRepository.save(credential);
        }

        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(normalizedEmail);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setVerificationStatus(VerificationStatus.PENDING);
        user.setActive(true);

        User savedUser = userRepository.save(user);

        String verificationToken = UUID.randomUUID().toString();
        VerificationToken token = new VerificationToken();
        token.setToken(verificationToken);
        token.setUserId(savedUser.getId());
        token.setTokenType("VERIFICATION");
        token.setExpiresAt(LocalDateTime.now().plusSeconds(verificationTokenExpiration));

        tokenRepository.save(token);
        emailService.sendVerificationEmail(savedUser.getEmail(), verificationToken);

        log.info("User registered: {} in tenant {}", savedUser.getEmail(), tenantId);
    }

    @Transactional(readOnly = true)
    public IdentifyResponse identify(String email, String password) {
        String normalizedEmail = email.toLowerCase();

        GlobalCredential credential = globalCredentialRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoderService.verifyPassword(password, credential.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        List<TenantSummary> tenants = userRepository.findAllByEmail(normalizedEmail).stream()
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
                .map(u -> tenantRepository.findById(u.getTenantId()).orElse(null))
                .filter(t -> t != null && t.getActive())
                .map(t -> new TenantSummary(t.getId(), t.getName(), t.getSubdomain()))
                .toList();

        if (tenants.isEmpty()) {
            throw new IllegalArgumentException("No active workspace found for this account");
        }

        List<UUID> tenantIds = tenants.stream().map(TenantSummary::id).toList();
        String preAuthToken = tokenProvider.generatePreAuthToken(normalizedEmail, tenantIds);

        return new IdentifyResponse(preAuthToken, tenants);
    }

    @Transactional
    public LoginResponse selectTenant(String preAuthToken, UUID tenantId) {
        if (!tokenProvider.isPreAuthToken(preAuthToken) || !tokenProvider.isTokenValid(preAuthToken)) {
            throw new IllegalArgumentException("Session expired. Please log in again.");
        }

        String email = tokenProvider.getEmailFromPreAuthToken(preAuthToken);
        List<UUID> allowedTenants = tokenProvider.getAllowedTenantsFromPreAuthToken(preAuthToken);

        if (!allowedTenants.contains(tenantId)) {
            throw new IllegalArgumentException("Workspace not allowed for this session");
        }

        User user = userRepository.findByEmailAndTenantId(email, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("User not found in workspace"));

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new IllegalArgumentException("Account is disabled");
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        List<String> permissions = tenantRoleService.getPermissionsForUser(user);
        String roleStr = user.getSystemRole() != null ? user.getSystemRole().name() : null;

        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), roleStr, user.getTenantId(), permissions);
        String refreshToken = tokenProvider.generateRefreshToken(user.getId(), user.getEmail());

        log.info("User logged in: {} in tenant {}", user.getEmail(), tenantId);

        return new LoginResponse(accessToken, refreshToken, tokenProvider.getAccessTokenTtlSeconds(), mapToUserDto(user),
                Boolean.TRUE.equals(user.getForcePasswordReset()));
    }

    @Transactional
    public LoginResponse login(LoginRequest request, UUID tenantId) {
        String normalizedEmail = request.email().toLowerCase();

        GlobalCredential credential = globalCredentialRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoderService.verifyPassword(request.password(), credential.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        User user = userRepository.findByEmailAndTenantId(normalizedEmail, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new IllegalArgumentException("Account is disabled");
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        List<String> permissions = tenantRoleService.getPermissionsForUser(user);
        String roleStr = user.getSystemRole() != null ? user.getSystemRole().name() : null;

        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), roleStr, user.getTenantId(), permissions);
        String refreshToken = tokenProvider.generateRefreshToken(user.getId(), user.getEmail());

        log.info("User logged in (host-resolved): {}", user.getEmail());

        return new LoginResponse(accessToken, refreshToken, tokenProvider.getAccessTokenTtlSeconds(), mapToUserDto(user),
                Boolean.TRUE.equals(user.getForcePasswordReset()));
    }

    @Transactional
    public void logout(UUID userId) {
        tokenService.invalidateUserTokens(userId);
        log.info("User logged out: {}", userId);
    }

    @Transactional
    public void verifyEmail(String token) {
        VerificationToken verificationToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (verificationToken.isExpired()) {
            throw new IllegalArgumentException("Verification token has expired");
        }

        User user = userRepository.findById(verificationToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setVerificationStatus(VerificationStatus.VERIFIED);
        user.setEmailVerifiedAt(LocalDateTime.now());
        userRepository.save(user);

        tokenRepository.delete(verificationToken);

        log.info("Email verified for user: {}", user.getEmail());
    }

    @Transactional
    public void requestPasswordReset(String email) {
        String normalizedEmail = email.toLowerCase();

        if (globalCredentialRepository.findByEmail(normalizedEmail).isEmpty()) {
            log.info("Password reset requested for non-existent email: {}", normalizedEmail);
            return;
        }

        Optional<User> userOpt = userRepository.findAllByEmail(normalizedEmail).stream()
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
                .findFirst();

        if (userOpt.isEmpty()) {
            log.info("Password reset: no active user found for email: {}", normalizedEmail);
            return;
        }

        User user = userOpt.get();

        List<VerificationToken> existingTokens = tokenRepository.findByUserIdAndTokenType(user.getId(), "PASSWORD_RESET");
        tokenRepository.deleteAll(existingTokens);

        String resetToken = UUID.randomUUID().toString();
        VerificationToken token = new VerificationToken();
        token.setToken(resetToken);
        token.setUserId(user.getId());
        token.setTokenType("PASSWORD_RESET");
        token.setExpiresAt(LocalDateTime.now().plusSeconds(resetTokenExpiration));

        tokenRepository.save(token);
        emailService.sendPasswordResetEmail(user.getEmail(), resetToken);

        log.info("Password reset requested for: {}", user.getEmail());
    }

    @Transactional
    public void resetPassword(String token, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        VerificationToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid reset token"));

        if (resetToken.isExpired() || !"PASSWORD_RESET".equals(resetToken.getTokenType())) {
            throw new IllegalArgumentException("Reset token has expired or is invalid");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        GlobalCredential credential = globalCredentialRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Credential not found"));

        credential.setPasswordHash(passwordEncoderService.encodePassword(newPassword));
        globalCredentialRepository.save(credential);

        tokenRepository.delete(resetToken);

        log.info("Password reset for: {}", user.getEmail());
    }

    public MeResponse getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String tenantIdStr = TenantContext.getTenantId();
        UUID tenantId = null;
        String tenantName = null;
        String tenantSubdomain = null;

        if (tenantIdStr != null) {
            try {
                tenantId = UUID.fromString(tenantIdStr);
                Optional<Tenant> tenant = tenantRepository.findById(tenantId);
                if (tenant.isPresent()) {
                    tenantName = tenant.get().getName();
                    tenantSubdomain = tenant.get().getSubdomain();
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        Set<String> supportPermissions = Set.of();
        if (user.getSystemRole() != null && user.getSystemRole().isInternalStaff()) {
            supportPermissions = new LinkedHashSet<>(supportUserPermissionRepository.findPermissionValuesByUserId(userId));
        }

        OnboardingSummaryResponse onboarding = null;
        if (user.getSystemRole() == null || !user.getSystemRole().isInternalStaff()) {
            onboarding = userOnboardingService.getSummaryOrDefault(
                    user.getTenantId(),
                    user.getId(),
                    UserOnboardingService.APP_CRM_CORE_V1
            );
        }

        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getActive(),
                user.getSystemRole(),
                user.getCreatedAt(),
                tenantId,
                tenantName,
                tenantSubdomain,
                supportPermissions,
                onboarding
        );
    }

    public UserDto getUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return mapToUserDto(user);
    }

    @Transactional
    public UserDto updateUserProfile(UUID userId, String firstName, String lastName, String phone) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhone(phone);
        User updatedUser = userRepository.save(user);

        log.info("User profile updated: {}", user.getEmail());
        return mapToUserDto(updatedUser);
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        GlobalCredential credential = globalCredentialRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Credential not found"));

        if (!passwordEncoderService.verifyPassword(currentPassword, credential.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        credential.setPasswordHash(passwordEncoderService.encodePassword(newPassword));
        globalCredentialRepository.save(credential);

        if (Boolean.TRUE.equals(user.getForcePasswordReset())) {
            user.setForcePasswordReset(false);
            userRepository.save(user);
        }

        log.info("Password changed for: {}", user.getEmail());
    }

    @Transactional
    public void requestEmailChange(UUID userId, String newEmail, String currentPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String currentEmail = user.getEmail();
        String normalizedEmail = newEmail.trim().toLowerCase();

        if (currentEmail.equals(normalizedEmail)) {
            throw new IllegalArgumentException("New email must be different from the current email");
        }

        GlobalCredential credential = globalCredentialRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found"));
        if (!passwordEncoderService.verifyPassword(currentPassword, credential.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (globalCredentialRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("Email is already registered");
        }

        tokenRepository.deleteAll(tokenRepository.findByUserIdAndTokenType(userId, "EMAIL_CHANGE"));
        VerificationToken token = new VerificationToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUserId(userId);
        token.setTokenType("EMAIL_CHANGE");
        token.setPendingEmail(normalizedEmail);
        token.setExpiresAt(LocalDateTime.now().plusSeconds(verificationTokenExpiration));
        tokenRepository.save(token);
        emailService.sendEmailChangeVerification(normalizedEmail, token.getToken());

        log.info("Email change requested for user: {}", userId);
    }

    @Transactional
    public void confirmEmailChange(String token) {
        VerificationToken verificationToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email change token"));
        if (!"EMAIL_CHANGE".equals(verificationToken.getTokenType()) || verificationToken.isExpired()
                || verificationToken.getPendingEmail() == null || verificationToken.getPendingEmail().isBlank()) {
            throw new IllegalArgumentException("Email change token is invalid or has expired");
        }

        User requestingUser = userRepository.findById(verificationToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String previousEmail = requestingUser.getEmail();
        String newEmail = verificationToken.getPendingEmail();
        if (globalCredentialRepository.findByEmail(newEmail).isPresent()) {
            throw new IllegalArgumentException("Email is already registered");
        }

        GlobalCredential credential = globalCredentialRepository.findByEmail(previousEmail)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found"));
        List<User> linkedUsers = userRepository.findAllByEmail(previousEmail);
        for (User linkedUser : linkedUsers) {
            linkedUser.setEmail(newEmail);
        }
        userRepository.saveAll(linkedUsers);

        GlobalCredential replacement = new GlobalCredential();
        replacement.setEmail(newEmail);
        replacement.setPasswordHash(credential.getPasswordHash());
        globalCredentialRepository.save(replacement);
        globalCredentialRepository.delete(credential);
        for (User linkedUser : linkedUsers) {
            tokenService.invalidateUserTokens(linkedUser.getId());
        }
        tokenRepository.delete(verificationToken);

        log.info("Email changed for {} linked user(s)", linkedUsers.size());
    }

    private UserDto mapToUserDto(User user) {
        Set<String> supportPermissions = Set.of();
        if (user.getSystemRole() != null && user.getSystemRole().isInternalStaff()) {
            supportPermissions = new LinkedHashSet<>(supportUserPermissionRepository.findPermissionValuesByUserId(user.getId()));
        }
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getActive(),
                user.getSystemRole(),
                user.getCreatedAt(),
                supportPermissions
        );
    }
}
