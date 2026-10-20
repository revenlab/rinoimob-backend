package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.IntegrationRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import java.time.Instant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationRouteRepository extends JpaRepository<IntegrationRoute, UUID> {
    interface SyncTarget {
        UUID getId();
        UUID getTenantId();
    }

    // This scheduler discovery query spans tenants; all subsequent reads and writes use tenantId.
    @Query(value = """
            SELECT r.id, r.tenant_id AS "tenantId" FROM integration_routes r
            JOIN integration_connections c ON c.id = r.connection_id AND c.tenant_id = r.tenant_id
            WHERE r.active = TRUE AND c.provider = 'META_LEAD_ADS' AND c.status = 'ACTIVE'
              AND r.meta_next_sync_at <= :now
            ORDER BY r.meta_next_sync_at, r.id LIMIT 50
            """, nativeQuery = true)
    List<SyncTarget> findDueMetaSyncTargets(@Param("now") Instant now);

    @Query(value = """
            SELECT r.* FROM integration_routes r WHERE r.id = :id AND r.tenant_id = :tenantId
              AND r.active = TRUE AND r.meta_next_sync_at <= :now
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<IntegrationRoute> lockDueMetaSyncRoute(@Param("tenantId") UUID tenantId,
            @Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("update IntegrationRoute r set r.metaNextSyncAt = :now where r.tenantId = :tenantId and r.connectionId = :connectionId and r.active = true")
    int requestMetaSync(@Param("tenantId") UUID tenantId, @Param("connectionId") UUID connectionId,
            @Param("now") Instant now);
    List<IntegrationRoute> findAllByTenantIdAndConnectionIdOrderByExternalResourceNameAsc(UUID tenantId, UUID connectionId);
    Optional<IntegrationRoute> findByTenantIdAndConnectionIdAndExternalResourceId(UUID tenantId, UUID connectionId, String externalResourceId);
    Optional<IntegrationRoute> findByTenantIdAndConnectionIdAndExternalResourceIdAndActiveTrue(UUID tenantId, UUID connectionId, String externalResourceId);
    Optional<IntegrationRoute> findByIdAndTenantId(UUID id, UUID tenantId);
}
