package com.rinoimob.config;

import com.rinoimob.domain.entity.GlobalCredential;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.enums.SystemRole;
import com.rinoimob.domain.enums.VerificationStatus;
import com.rinoimob.domain.repository.GlobalCredentialRepository;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.TenantRoleService;
import com.rinoimob.service.auth.PasswordEncoderService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevDataSeeder implements ApplicationRunner {

    private static final String DEMO_PASSWORD = "Dev@12345";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final GlobalCredentialRepository globalCredentialRepository;
    private final PasswordEncoderService passwordEncoderService;
    private final TenantRoleService tenantRoleService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedSupportWorkspace();
        seedDemoWorkspace();
    }

    private void seedSupportWorkspace() {
        Tenant tenant = ensureTenant("Rino Support", "support");
        tenant.setActive(true);
        tenant = tenantRepository.save(tenant);
        tenantRoleService.seedDefaultRoles(tenant.getId());

        ensureUser(tenant.getId(), "suporte@rinoimob.com", "Rino", "Support", SystemRole.TENANT_ADMIN, null);
        ensureUser(tenant.getId(), "gestor.suporte@rinoimob.com", "Rino", "Manager", SystemRole.SUPPORT_MANAGER, null);
        ensureUser(tenant.getId(), "agente.suporte@rinoimob.com", "Rino", "Agent", SystemRole.SUPPORT_AGENT, null);
    }

    private void seedDemoWorkspace() {
        Tenant tenant = ensureTenant("Demo Rino", "demo");
        tenant.setActive(true);
        tenant = tenantRepository.save(tenant);
        tenantRoleService.seedDefaultRoles(tenant.getId());

        List<User> existingUsers = userRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId());
        if (existingUsers.isEmpty()) {
            User owner = ensureUser(tenant.getId(), "owner@demo.local", "Demo", "Owner", SystemRole.TENANT_OWNER, null);
            ensureUser(tenant.getId(), "corretor@demo.local", "Demo", "Corretor", null, findDefaultRoleId(tenant.getId()));
            log.info("Dev demo workspace seeded with owner {}", owner.getEmail());
        }
    }

    private Tenant ensureTenant(String name, String subdomain) {
        return tenantRepository.findBySubdomain(subdomain)
                .orElseGet(() -> {
                    Tenant tenant = new Tenant();
                    tenant.setName(name);
                    tenant.setSubdomain(subdomain);
                    tenant.setActive(true);
                    return tenantRepository.save(tenant);
                });
    }

    private User ensureUser(java.util.UUID tenantId, String email, String firstName, String lastName,
                            SystemRole systemRole, java.util.UUID tenantRoleId) {
        return userRepository.findByEmailAndTenantId(email, tenantId)
                .orElseGet(() -> {
                    ensureCredential(email);

                    User user = new User();
                    user.setTenantId(tenantId);
                    user.setEmail(email);
                    user.setFirstName(firstName);
                    user.setLastName(lastName);
                    user.setSystemRole(systemRole);
                    user.setTenantRoleId(tenantRoleId);
                    user.setVerificationStatus(VerificationStatus.VERIFIED);
                    user.setEmailVerifiedAt(LocalDateTime.now());
                    user.setActive(true);
                    return userRepository.save(user);
                });
    }

    private void ensureCredential(String email) {
        if (globalCredentialRepository.findByEmail(email).isPresent()) {
            return;
        }

        GlobalCredential credential = new GlobalCredential();
        credential.setEmail(email);
        credential.setPasswordHash(passwordEncoderService.encodePassword(DEMO_PASSWORD));
        globalCredentialRepository.save(credential);
    }

    private java.util.UUID findDefaultRoleId(java.util.UUID tenantId) {
        return tenantRoleService.listRoles(tenantId).stream()
                .filter(role -> "Corretor".equalsIgnoreCase(role.name()))
                .map(role -> role.id())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Default role Corretor not found for tenant " + tenantId));
    }
}
