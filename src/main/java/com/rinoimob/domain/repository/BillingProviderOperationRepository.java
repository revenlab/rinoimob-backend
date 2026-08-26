package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.BillingProviderOperation;
import com.rinoimob.domain.enums.BillingProviderOperationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BillingProviderOperationRepository extends JpaRepository<BillingProviderOperation, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query(value = """
            INSERT INTO billing_provider_operations (
                id, tenant_id, operation_type, provider_resource_id, idempotency_key, payload,
                status, attempt_count, next_attempt_at, created_at, updated_at
            ) VALUES (
                :id, :tenantId, :operationType, :providerResourceId, :idempotencyKey,
                CAST(:payloadJson AS jsonb), 'PENDING', 0, :createdAt, :createdAt, :createdAt
            )
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("tenantId") UUID tenantId,
                       @Param("operationType") String operationType,
                       @Param("providerResourceId") String providerResourceId,
                       @Param("idempotencyKey") String idempotencyKey,
                       @Param("payloadJson") String payloadJson,
                       @Param("createdAt") LocalDateTime createdAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BillingProviderOperation> findTop50ByStatusInAndNextAttemptAtBeforeOrderByCreatedAtAsc(
            Collection<BillingProviderOperationStatus> statuses, LocalDateTime now);
}
