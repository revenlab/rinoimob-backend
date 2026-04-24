package com.rinoimob.service.auth;

import com.rinoimob.domain.dto.LoginRequest;
import com.rinoimob.domain.dto.RegisterRequest;
import com.rinoimob.domain.dto.TenantRegistrationRequest;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.entity.VerificationToken;
import com.rinoimob.domain.enums.Role;
import com.rinoimob.domain.enums.VerificationStatus;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.domain.repository.VerificationTokenRepository;
import com.rinoimob.service.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import com.rinoimob.config.security.JwtTokenProvider;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("AuthService Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private VerificationTokenRepository tokenRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private PasswordEncoderService passwordEncoderService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should register user successfully")
    void testRegisterSuccess() {
        RegisterRequest request = new RegisterRequest(
                "John", "Doe", "john@test.com", "Password123", "Password123"
        );

        when(userRepository.existsByEmailAndTenantId(request.email(), tenantId)).thenReturn(false);
        when(passwordEncoderService.encodePassword(request.password())).thenReturn("hashed_password");

        User savedUser = new User();
        savedUser.setId(userId);
        savedUser.setEmail(request.email());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        authService.register(request, tenantId);

        verify(userRepository, times(1)).save(any(User.class));
        verify(tokenRepository, times(1)).save(any(VerificationToken.class));
        verify(emailService, times(1)).sendVerificationEmail(eq(request.email()), anyString());
    }

    @Test
    @DisplayName("Should throw error when passwords don't match")
    void testRegisterPasswordMismatch() {
        RegisterRequest request = new RegisterRequest(
                "John", "Doe", "john@test.com", "Password123", "WrongPassword"
        );

        assertThrows(IllegalArgumentException.class, () -> authService.register(request, tenantId));
    }

    @Test
    @DisplayName("Should throw error when email already registered")
    void testRegisterEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest(
                "John", "Doe", "john@test.com", "Password123", "Password123"
        );

        when(userRepository.existsByEmailAndTenantId(request.email(), tenantId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request, tenantId));
    }

    @Test
    @DisplayName("Should login user successfully")
    void testLoginSuccess() {
        LoginRequest request = new LoginRequest("john@test.com", "Password123");

        User user = new User();
        user.setId(userId);
        user.setEmail(request.email());
        user.setPasswordHash("hashed_password");
        user.setActive(true);
        user.setRole(Role.USER);
        user.setTenantId(tenantId);

        when(userRepository.findByEmailAndTenantId(request.email(), tenantId)).thenReturn(Optional.of(user));
        when(passwordEncoderService.verifyPassword(request.password(), user.getPasswordHash())).thenReturn(true);
        when(tokenProvider.generateAccessToken(userId, request.email(), Role.USER.toString(), tenantId))
                .thenReturn("access_token");
        when(tokenProvider.generateRefreshToken(userId, request.email())).thenReturn("refresh_token");

        var response = authService.login(request, tenantId);

        assertNotNull(response);
        assertEquals("access_token", response.accessToken());
        assertEquals("refresh_token", response.refreshToken());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw error on invalid credentials")
    void testLoginInvalidCredentials() {
        LoginRequest request = new LoginRequest("john@test.com", "WrongPassword");

        when(userRepository.findByEmailAndTenantId(request.email(), tenantId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> authService.login(request, tenantId));
    }

    @Test
    @DisplayName("Should verify email successfully")
    void testVerifyEmailSuccess() {
        String token = "verification_token";
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setUserId(userId);
        verificationToken.setExpiresAt(LocalDateTime.now().plusHours(24));

        User user = new User();
        user.setId(userId);
        user.setEmail("john@test.com");
        user.setVerificationStatus(VerificationStatus.PENDING);

        when(tokenRepository.findByToken(token)).thenReturn(Optional.of(verificationToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        authService.verifyEmail(token);

        assertEquals(VerificationStatus.VERIFIED, user.getVerificationStatus());
        assertNotNull(user.getEmailVerifiedAt());
        verify(userRepository, times(1)).save(user);
        verify(tokenRepository, times(1)).delete(verificationToken);
    }

    @Test
    @DisplayName("Should throw error for expired verification token")
    void testVerifyEmailExpiredToken() {
        String token = "expired_token";
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setUserId(userId);
        verificationToken.setExpiresAt(LocalDateTime.now().minusHours(1));

        when(tokenRepository.findByToken(token)).thenReturn(Optional.of(verificationToken));

        assertThrows(IllegalArgumentException.class, () -> authService.verifyEmail(token));
    }

    @Test
    @DisplayName("Should request password reset successfully")
    void testRequestPasswordResetSuccess() {
        String email = "john@test.com";
        User user = new User();
        user.setId(userId);
        user.setEmail(email);

        when(userRepository.findByEmailAndTenantId(email, tenantId)).thenReturn(Optional.of(user));
        when(tokenRepository.findByUserIdAndTokenType(userId, "PASSWORD_RESET")).thenReturn(java.util.Collections.emptyList());

        authService.requestPasswordReset(email, tenantId);

        verify(tokenRepository, times(1)).save(any(VerificationToken.class));
        verify(emailService, times(1)).sendPasswordResetEmail(eq(email), anyString());
    }

    @Test
    @DisplayName("Should silently return when email not found in password reset")
    void testRequestPasswordResetEmailNotFound() {
        String email = "nonexistent@test.com";
        when(userRepository.findByEmailAndTenantId(email, tenantId)).thenReturn(Optional.empty());

        authService.requestPasswordReset(email, tenantId);

        verify(tokenRepository, never()).save(any(VerificationToken.class));
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("Should reset password successfully")
    void testResetPasswordSuccess() {
        String token = "reset_token";
        String newPassword = "NewPassword123";
        String confirmPassword = "NewPassword123";

        VerificationToken resetToken = new VerificationToken();
        resetToken.setUserId(userId);
        resetToken.setTokenType("PASSWORD_RESET");
        resetToken.setExpiresAt(LocalDateTime.now().plusHours(1));

        User user = new User();
        user.setId(userId);
        user.setEmail("john@test.com");

        when(tokenRepository.findByToken(token)).thenReturn(Optional.of(resetToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoderService.encodePassword(newPassword)).thenReturn("hashed_new_password");

        authService.resetPassword(token, newPassword, confirmPassword);

        assertEquals("hashed_new_password", user.getPasswordHash());
        verify(userRepository, times(1)).save(user);
        verify(tokenRepository, times(1)).delete(resetToken);
    }

    @Test
    @DisplayName("Should sign up a new tenant and owner successfully")
    void testSignupSuccess() {
        TenantRegistrationRequest request = new TenantRegistrationRequest(
                "Acme Corp", "acme", "John", "Doe", "john@acme.com", "Password123", "Password123"
        );

        when(tenantRepository.findBySubdomain("acme")).thenReturn(Optional.empty());
        when(tenantRepository.findByName("Acme Corp")).thenReturn(Optional.empty());

        Tenant savedTenant = new Tenant();
        savedTenant.setId(UUID.randomUUID());
        savedTenant.setSubdomain("acme");
        when(tenantRepository.save(any(Tenant.class))).thenReturn(savedTenant);

        when(passwordEncoderService.encodePassword(request.password())).thenReturn("hashed_password");

        User savedUser = new User();
        savedUser.setId(userId);
        savedUser.setEmail(request.email());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        authService.signup(request);

        verify(tenantRepository, times(1)).save(any(Tenant.class));
        verify(userRepository, times(1)).save(argThat(u -> u.getRole() == Role.TENANT_OWNER));
        verify(tokenRepository, times(1)).save(any(VerificationToken.class));
        verify(emailService, times(1)).sendVerificationEmail(eq(request.email()), anyString());
    }

    @Test
    @DisplayName("Should normalize subdomain to lowercase on signup")
    void testSignupNormalizesSubdomain() {
        TenantRegistrationRequest request = new TenantRegistrationRequest(
                "Acme Corp", "ACME", "John", "Doe", "john@acme.com", "Password123", "Password123"
        );

        when(tenantRepository.findBySubdomain("acme")).thenReturn(Optional.empty());
        when(tenantRepository.findByName("Acme Corp")).thenReturn(Optional.empty());

        Tenant savedTenant = new Tenant();
        savedTenant.setId(UUID.randomUUID());
        when(tenantRepository.save(any(Tenant.class))).thenReturn(savedTenant);

        User savedUser = new User();
        savedUser.setId(userId);
        savedUser.setEmail(request.email());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        authService.signup(request);

        verify(tenantRepository, times(1)).save(argThat(t -> "acme".equals(t.getSubdomain())));
    }

    @Test
    @DisplayName("Should throw error on signup when passwords don't match")
    void testSignupPasswordMismatch() {
        TenantRegistrationRequest request = new TenantRegistrationRequest(
                "Acme Corp", "acme", "John", "Doe", "john@acme.com", "Password123", "WrongPassword"
        );

        assertThrows(IllegalArgumentException.class, () -> authService.signup(request));
        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    @DisplayName("Should throw error on signup when subdomain is already taken")
    void testSignupSubdomainAlreadyTaken() {
        TenantRegistrationRequest request = new TenantRegistrationRequest(
                "Acme Corp", "acme", "John", "Doe", "john@acme.com", "Password123", "Password123"
        );

        Tenant existingTenant = new Tenant();
        when(tenantRepository.findBySubdomain("acme")).thenReturn(Optional.of(existingTenant));

        assertThrows(IllegalArgumentException.class, () -> authService.signup(request));
        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    @DisplayName("Should throw error on signup when tenant name is already taken")
    void testSignupTenantNameAlreadyTaken() {
        TenantRegistrationRequest request = new TenantRegistrationRequest(
                "Acme Corp", "acme", "John", "Doe", "john@acme.com", "Password123", "Password123"
        );

        when(tenantRepository.findBySubdomain("acme")).thenReturn(Optional.empty());
        Tenant existingTenant = new Tenant();
        when(tenantRepository.findByName("Acme Corp")).thenReturn(Optional.of(existingTenant));

        assertThrows(IllegalArgumentException.class, () -> authService.signup(request));
        verify(tenantRepository, never()).save(any(Tenant.class));
    }
}
