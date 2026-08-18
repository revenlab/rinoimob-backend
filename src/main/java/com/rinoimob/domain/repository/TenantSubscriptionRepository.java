package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.TenantSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import com.rinoimob.domain.enums.BillingSubscriptionStatus;

@Repository
public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, UUID> {

    Optional<TenantSubscription> findByTenantId(UUID tenantId);

    List<TenantSubscription> findAllByStatusAndPastDueAtBefore(BillingSubscriptionStatus status, LocalDateTime cutoff);
}
