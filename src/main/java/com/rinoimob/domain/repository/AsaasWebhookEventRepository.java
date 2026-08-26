package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.AsaasWebhookEvent;
import com.rinoimob.domain.enums.AsaasWebhookEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AsaasWebhookEventRepository extends JpaRepository<AsaasWebhookEvent, UUID> {

    boolean existsByProviderEventId(String providerEventId);

    Optional<AsaasWebhookEvent> findByProviderEventId(String providerEventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AsaasWebhookEvent> findTop50ByStatusInAndNextAttemptAtBeforeOrderByReceivedAtAsc(
            Collection<AsaasWebhookEventStatus> statuses, LocalDateTime now);

    @Modifying
    @Query(value = """
            INSERT INTO asaas_webhook_events (
                id, provider_event_id, event_type, provider_account_id, resource_type, resource_id,
                payload, status, attempt_count, next_attempt_at, provider_created_at, received_at, updated_at
            ) VALUES (
                :id, :providerEventId, :eventType, :providerAccountId, :resourceType, :resourceId,
                CAST(:payloadJson AS jsonb), 'RECEIVED', 0, :receivedAt, :providerCreatedAt, :receivedAt, :receivedAt
            )
            ON CONFLICT (provider_event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("providerEventId") String providerEventId,
                       @Param("eventType") String eventType,
                       @Param("providerAccountId") String providerAccountId,
                       @Param("resourceType") String resourceType,
                       @Param("resourceId") String resourceId,
                       @Param("payloadJson") String payloadJson,
                       @Param("providerCreatedAt") LocalDateTime providerCreatedAt,
                       @Param("receivedAt") LocalDateTime receivedAt);
}
