package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.IntegrationRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationRouteRepository extends JpaRepository<IntegrationRoute, UUID> {
    List<IntegrationRoute> findAllByTenantIdAndConnectionIdOrderByExternalResourceNameAsc(UUID tenantId, UUID connectionId);
    Optional<IntegrationRoute> findByTenantIdAndConnectionIdAndExternalResourceId(UUID tenantId, UUID connectionId, String externalResourceId);
    Optional<IntegrationRoute> findByTenantIdAndConnectionIdAndExternalResourceIdAndActiveTrue(UUID tenantId, UUID connectionId, String externalResourceId);
    Optional<IntegrationRoute> findByIdAndTenantId(UUID id, UUID tenantId);
}
