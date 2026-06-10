package com.rinoimob.service.core;

import com.rinoimob.context.TenantContext;
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
import com.rinoimob.service.auth.PasswordEncoderService;
import com.rinoimob.service.auth.TokenService;
import com.rinoimob.service.billing.TenantQuotaEnforcementService;
import com.rinoimob.service.email.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_ID   = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID ROLE_ID   = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock private UserRepository userRepository;
    @Mock private TenantRoleRepository tenantRoleRepository;
    @Mock private TokenService tokenService;
    @Mock private TenantRoleService tenantRoleService;
    @Mock private GlobalCredentialRepository globalCredentialRepository;
    @Mock private PasswordEncoderService passwordEncoderService;
    @Mock private VerificationTokenRepository verificationTokenRepository;
    @Mock private EmailService emailService;
    @Mock private TenantQuotaEnforcementService tenantQuotaEnforcementService;

    private UserManagementService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID.toString());
        service = new UserManagementService(
                userRepository, tenantRoleRepository, tokenService, tenantRoleService,
                globalCredentialRepository, passwordEncoderService, verificationTokenRepository, emailService,
                tenantQuotaEnforcementService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── inviteUser ────────────────────────────────────────────────────────────

    @Test
    void inviteUser_createsUserAndVerificationToken_whenEmailNotRegistered() {
        InviteUserRequest req = new InviteUserRequest("test@example.com", "João", "Silva", null, ROLE_ID);

        when(userRepository.existsByEmailAndTenantId("test@example.com", TENANT_ID)).thenReturn(false);
        when(tenantRoleRepository.findByTenantIdAndId(TENANT_ID, ROLE_ID)).thenReturn(Optional.of(buildRole()));
        when(globalCredentialRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(passwordEncoderService.encodePassword(any())).thenReturn("hashed");

        User savedUser = buildPendingUser();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(tenantRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(buildRole()));

        UserManagementResponse response = service.inviteUser(TENANT_ID, req);

        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.verificationStatus()).isEqualTo(VerificationStatus.PENDING);
        verify(verificationTokenRepository).save(any(VerificationToken.class));
        verify(emailService).sendInvitationEmail(eq("test@example.com"), anyString(), eq("João"));
    }

    @Test
    void inviteUser_throwsConflict_whenEmailAlreadyRegisteredInTenant() {
        InviteUserRequest req = new InviteUserRequest("duplicate@example.com", "A", "B", null, ROLE_ID);

        when(userRepository.existsByEmailAndTenantId("duplicate@example.com", TENANT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.inviteUser(TENANT_ID, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void inviteUser_throwsNotFound_whenRoleDoesNotBelongToTenant() {
        InviteUserRequest req = new InviteUserRequest("new@example.com", "A", "B", null, ROLE_ID);

        when(userRepository.existsByEmailAndTenantId("new@example.com", TENANT_ID)).thenReturn(false);
        when(tenantRoleRepository.findByTenantIdAndId(TENANT_ID, ROLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.inviteUser(TENANT_ID, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Role not found");
    }

    // ── resendInvitation ──────────────────────────────────────────────────────

    @Test
    void resendInvitation_invalidatesOldTokensAndCreatesNewOne_whenUserIsPending() {
        User pendingUser = buildPendingUser();
        VerificationToken oldToken = buildVerificationToken();

        when(userRepository.findByIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(Optional.of(pendingUser));
        when(verificationTokenRepository.findByUserIdAndTokenType(USER_ID, "VERIFICATION"))
                .thenReturn(List.of(oldToken));

        service.resendInvitation(TENANT_ID, USER_ID);

        verify(verificationTokenRepository).delete(oldToken);
        ArgumentCaptor<VerificationToken> captor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(verificationTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getTokenType()).isEqualTo("VERIFICATION");
        assertThat(captor.getValue().getExpiresAt()).isAfter(LocalDateTime.now());
        verify(emailService).sendInvitationEmail(eq("test@example.com"), anyString(), eq("João"));
    }

    @Test
    void resendInvitation_throwsNotFound_whenUserDoesNotBelongToTenant() {
        when(userRepository.findByIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resendInvitation(TENANT_ID, USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void resendInvitation_throwsConflict_whenUserIsAlreadyVerified() {
        User verifiedUser = buildPendingUser();
        verifiedUser.setVerificationStatus(VerificationStatus.VERIFIED);

        when(userRepository.findByIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(Optional.of(verifiedUser));

        assertThatThrownBy(() -> service.resendInvitation(TENANT_ID, USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already accepted");
    }

    // ── deactivateUser ────────────────────────────────────────────────────────

    @Test
    void deactivateUser_throwsForbidden_whenUserIsTenantOwner() {
        User owner = buildPendingUser();
        owner.setSystemRole(SystemRole.TENANT_OWNER);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.deactivateUser(TENANT_ID, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User buildPendingUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setTenantId(TENANT_ID);
        user.setEmail("test@example.com");
        user.setFirstName("João");
        user.setLastName("Silva");
        user.setActive(true);
        user.setVerificationStatus(VerificationStatus.PENDING);
        user.setTenantRoleId(ROLE_ID);
        return user;
    }

    private TenantRole buildRole() {
        TenantRole role = new TenantRole();
        role.setId(ROLE_ID);
        role.setTenantId(TENANT_ID);
        role.setName("Agente");
        return role;
    }

    private VerificationToken buildVerificationToken() {
        VerificationToken token = new VerificationToken();
        token.setId(UUID.randomUUID());
        token.setToken(UUID.randomUUID().toString());
        token.setUserId(USER_ID);
        token.setTokenType("VERIFICATION");
        token.setExpiresAt(LocalDateTime.now().plusDays(1));
        return token;
    }
}
