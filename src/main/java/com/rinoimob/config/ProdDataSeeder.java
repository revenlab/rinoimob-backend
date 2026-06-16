package com.rinoimob.config;

import com.rinoimob.domain.entity.GlobalCredential;
import com.rinoimob.domain.entity.SupportUserPermission;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.enums.SupportPermission;
import com.rinoimob.domain.enums.SystemRole;
import com.rinoimob.domain.enums.VerificationStatus;
import com.rinoimob.domain.repository.GlobalCredentialRepository;
import com.rinoimob.domain.repository.SupportUserPermissionRepository;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.core.TenantRoleService;
import com.rinoimob.service.auth.PasswordEncoderService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class ProdDataSeeder implements ApplicationRunner {

    @Value("${support.admin.email:}")
    private String adminEmail;

    @Value("${support.admin.password:}")
    private String adminPassword;

    @Value("${support.admin.first-name:Rino}")
    private String adminFirstName;

    @Value("${support.admin.last-name:Support}")
    private String adminLastName;

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final GlobalCredentialRepository globalCredentialRepository;
    private final PasswordEncoderService passwordEncoderService;
    private final TenantRoleService tenantRoleService;
    private final SupportUserPermissionRepository supportUserPermissionRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(adminPassword)) {
            log.warn("ProdDataSeeder: SUPPORT_ADMIN_EMAIL or SUPPORT_ADMIN_PASSWORD not set — skipping support admin seed");
            return;
        }

        Tenant tenant = ensureTenant("Rino Support", "support");
        tenantRoleService.seedDefaultRoles(tenant.getId());

        User admin = ensureUser(tenant.getId(), adminEmail, adminFirstName, adminLastName);
        ensureAllSupportPermissions(admin);

        log.info("ProdDataSeeder: support admin account ready — {}", adminEmail);
    }

    private Tenant ensureTenant(String name, String subdomain) {
        return tenantRepository.findBySubdomain(subdomain).orElseGet(() -> {
            Tenant tenant = new Tenant();
            tenant.setName(name);
            tenant.setSubdomain(subdomain);
            tenant.setActive(true);
            return tenantRepository.save(tenant);
        });
    }

    private User ensureUser(UUID tenantId, String email, String firstName, String lastName) {
        return userRepository.findByEmailAndTenantId(email, tenantId).orElseGet(() -> {
            ensureCredential(email);

            User user = new User();
            user.setTenantId(tenantId);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setSystemRole(SystemRole.TENANT_ADMIN);
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
        credential.setPasswordHash(passwordEncoderService.encodePassword(adminPassword));
        globalCredentialRepository.save(credential);
    }

    private void ensureAllSupportPermissions(User user) {
        Set<String> existing = new HashSet<>(supportUserPermissionRepository.findPermissionValuesByUserId(user.getId()));
        List<SupportUserPermission> toAdd = new ArrayList<>();

        for (SupportPermission permission : SupportPermission.values()) {
            if (existing.contains(permission.getValue())) {
                continue;
            }
            SupportUserPermission entry = new SupportUserPermission();
            entry.setUserId(user.getId());
            entry.setPermission(permission.getValue());
            toAdd.add(entry);
        }

        if (!toAdd.isEmpty()) {
            supportUserPermissionRepository.saveAll(toAdd);
        }
    }
}
