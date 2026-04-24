package com.rinoimob.service.auth;

import com.rinoimob.config.security.JwtTokenProvider;
import com.rinoimob.domain.dto.LoginRequest;
import com.rinoimob.domain.dto.LoginResponse;
import com.rinoimob.domain.dto.RegisterRequest;
import com.rinoimob.domain.dto.TenantRegistrationRequest;
import com.rinoimob.domain.dto.UserDto;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.entity.VerificationToken;
import com.rinoimob.domain.enums.Role;
import com.rinoimob.domain.enums.VerificationStatus;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.domain.repository.VerificationTokenRepository;
import com.rinoimob.service.email.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final VerificationTokenRepository tokenRepository;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoderService passwordEncoderService;
    private final EmailService emailService;

    @Value("${app.verification-token-expiration:86400}")
    private long verificationTokenExpiration;

    @Value("${app.reset-token-expiration:3600}")
    private long resetTokenExpiration;

    public AuthService(UserRepository userRepository,
                       TenantRepository tenantRepository,
                       VerificationTokenRepository tokenRepository,
                       JwtTokenProvider tokenProvider,
                       PasswordEncoderService passwordEncoderService,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.tokenRepository = tokenRepository;
        this.tokenProvider = tokenProvider;
        this.passwordEncoderService = passwordEncoderService;
        this.emailService = emailService;
    }

    @Transactional
    public void signup(TenantRegistrationRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        String normalizedSubdomain = request.subdomain().toLowerCase();

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

        User user = new User();
        user.setTenantId(savedTenant.getId());
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPasswordHash(passwordEncoderService.encodePassword(request.password()));
        user.setRole(Role.TENANT_OWNER);
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

        log.info("New tenant '{}' created with owner: {}", savedTenant.getSubdomain(), savedUser.getEmail());
    }

    @Transactional
    public void register(RegisterRequest request, UUID tenantId) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        if (userRepository.existsByEmailAndTenantId(request.email(), tenantId)) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPasswordHash(passwordEncoderService.encodePassword(request.password()));
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

    @Transactional
    public LoginResponse login(LoginRequest request, UUID tenantId) {
        User user = userRepository.findByEmailAndTenantId(request.email(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!user.getActive()) {
            throw new IllegalArgumentException("Account is disabled");
        }

        if (!passwordEncoderService.verifyPassword(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole().toString(), user.getTenantId());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId(), user.getEmail());

        log.info("User logged in: {}", user.getEmail());

        return new LoginResponse(
                accessToken,
                refreshToken,
                900L,
                mapToUserDto(user)
        );
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
    public void requestPasswordReset(String email, UUID tenantId) {
        Optional<User> userOpt = userRepository.findByEmailAndTenantId(email, tenantId);
        if (userOpt.isEmpty()) {
            log.info("Password reset requested for non-existent email: {}", email);
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

        log.info("Password reset requested for user: {}", user.getEmail());
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

        user.setPasswordHash(passwordEncoderService.encodePassword(newPassword));
        userRepository.save(user);

        tokenRepository.delete(resetToken);

        log.info("Password reset for user: {}", user.getEmail());
    }

    public UserDto getUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return mapToUserDto(user);
    }

    @Transactional
    public UserDto updateUserProfile(UUID userId, String firstName, String lastName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setFirstName(firstName);
        user.setLastName(lastName);
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

        if (!passwordEncoderService.verifyPassword(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoderService.encodePassword(newPassword));
        userRepository.save(user);

        log.info("Password changed for user: {}", user.getEmail());
    }

    private UserDto mapToUserDto(User user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getActive(),
                user.getCreatedAt()
        );
    }
}
