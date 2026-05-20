package com.rinoimob.config;

import com.rinoimob.domain.dto.TenantRoleResponse;
import com.rinoimob.domain.entity.GlobalCredential;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.enums.SystemRole;
import com.rinoimob.domain.repository.GlobalCredentialRepository;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.TenantRoleService;
import com.rinoimob.service.auth.PasswordEncoderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DevDataSeederTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private GlobalCredentialRepository globalCredentialRepository;
    @Mock private PasswordEncoderService passwordEncoderService;
    @Mock private TenantRoleService tenantRoleService;

    @Test
    void shouldSeedSupportAndDemoAccountsWhenRunningInDev() {
        when(tenantRepository.findBySubdomain("support")).thenReturn(Optional.empty());
        when(tenantRepository.findBySubdomain("demo")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(UUID.randomUUID());
            return tenant;
        });
        when(userRepository.findByEmailAndTenantId(any(), any())).thenReturn(Optional.empty());
        when(globalCredentialRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(passwordEncoderService.encodePassword(any())).thenReturn("hashed");
        when(tenantRoleService.listRoles(any())).thenReturn(List.of(
                new TenantRoleResponse(UUID.randomUUID(), UUID.randomUUID(), "Corretor", null, false, List.of())
        ));
        when(userRepository.findByTenantIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DevDataSeeder seeder = new DevDataSeeder(
                tenantRepository,
                userRepository,
                globalCredentialRepository,
                passwordEncoderService,
                tenantRoleService);

        seeder.run(mock(ApplicationArguments.class));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());

        assertThat(userCaptor.getAllValues())
                .extracting(User::getSystemRole)
                .contains(SystemRole.TENANT_ADMIN, SystemRole.TENANT_OWNER);

        verify(globalCredentialRepository, atLeastOnce()).save(any(GlobalCredential.class));
        verify(tenantRoleService, atLeast(2)).seedDefaultRoles(any());
    }
}
