package com.rinoimob.service;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.repository.TenantRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public Tenant createTenant(String name, String subdomain) {
        Tenant tenant = new Tenant();
        tenant.setName(name);
        tenant.setSubdomain(subdomain);
        return tenantRepository.save(tenant);
    }

    public Optional<Tenant> getTenantById(String id) {
        return tenantRepository.findById(id);
    }

    public Optional<Tenant> getTenantBySubdomain(String subdomain) {
        return tenantRepository.findBySubdomain(subdomain);
    }

    public String getCurrentTenantId() {
        return TenantContext.getTenantId();
    }

    public Optional<Tenant> getCurrentTenant() {
        String tenantId = getCurrentTenantId();
        if (tenantId == null) {
            return Optional.empty();
        }
        return getTenantById(tenantId);
    }

    public Tenant updateTenant(String id, String name, String subdomain) {
        Tenant tenant = tenantRepository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("Tenant not found: " + id)
        );
        tenant.setName(name);
        tenant.setSubdomain(subdomain);
        return tenantRepository.save(tenant);
    }

    public void deactivateTenant(String id) {
        Tenant tenant = tenantRepository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("Tenant not found: " + id)
        );
        tenant.setActive(false);
        tenantRepository.save(tenant);
    }

}
