package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.IntegrationEvent;
import com.rinoimob.domain.enums.IntegrationEventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationEventRepository extends JpaRepository<IntegrationEvent, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO integration_events(id, tenant_id, connection_id, provider, external_event_id,
                external_resource_id, event_type, status, encrypted_payload, attempt_count,
                next_attempt_at, received_at, updated_at)
            SELECT :id, :tenantId, :connectionId, :provider, :externalId, :resourceId, :eventType,
                :status, :payload, 0, :now, :now, :now
            WHERE EXISTS (SELECT 1 FROM integration_connections c
                WHERE c.id = :connectionId AND c.tenant_id = :tenantId AND c.provider = :provider)
              AND NOT EXISTS (SELECT 1 FROM lead_acquisitions a
                WHERE a.tenant_id = :tenantId AND a.connection_id = :connectionId
                  AND a.external_lead_id = :externalId)
            ON CONFLICT (connection_id, external_event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
            @Param("connectionId") UUID connectionId, @Param("provider") String provider,
            @Param("externalId") String externalId, @Param("resourceId") String resourceId,
            @Param("eventType") String eventType, @Param("status") String status,
            @Param("payload") String payload, @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
            update IntegrationEvent e set e.status = com.rinoimob.domain.enums.IntegrationEventStatus.RECEIVED,
                e.attemptCount = 0, e.lastError = null, e.nextAttemptAt = :now
            where e.tenantId = :tenantId and e.connectionId = :connectionId
              and e.externalResourceId = :resourceId and e.leadId is null
              and e.status in (com.rinoimob.domain.enums.IntegrationEventStatus.FAILED,
                  com.rinoimob.domain.enums.IntegrationEventStatus.IGNORED)
            """)
    int requeueAfterConfigurationChange(@Param("tenantId") UUID tenantId,
            @Param("connectionId") UUID connectionId, @Param("resourceId") String resourceId,
            @Param("now") LocalDateTime now);
    Optional<IntegrationEvent> findByConnectionIdAndExternalEventId(UUID connectionId, String externalEventId);
    List<IntegrationEvent> findTop50ByStatusInAndNextAttemptAtBeforeOrderByReceivedAtAsc(Collection<IntegrationEventStatus> statuses, LocalDateTime now);
    Page<IntegrationEvent> findAllByTenantIdOrderByReceivedAtDesc(UUID tenantId, Pageable pageable);
    Optional<IntegrationEvent> findByIdAndTenantId(UUID id, UUID tenantId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from IntegrationEvent event where event.id = :id")
    Optional<IntegrationEvent> findByIdForUpdate(@Param("id") UUID id);
    long countByConnectionIdAndStatusIn(UUID connectionId, Collection<IntegrationEventStatus> statuses);
    void deleteByReceivedAtBefore(LocalDateTime cutoff);
}
