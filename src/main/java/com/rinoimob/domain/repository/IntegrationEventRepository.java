package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.IntegrationEvent;
import com.rinoimob.domain.enums.IntegrationEventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
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
