package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.TenantRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRoleRepository extends JpaRepository<TenantRole, UUID> {
    List<TenantRole> findByTenantId(UUID tenantId);
    Optional<TenantRole> findByTenantIdAndId(UUID tenantId, UUID id);
    boolean existsByTenantIdAndName(UUID tenantId, String name);
}
