package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.TenantSubscriptionChange;
import com.rinoimob.domain.enums.BillingSubscriptionChangeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantSubscriptionChangeRepository extends JpaRepository<TenantSubscriptionChange, UUID> {

    Optional<TenantSubscriptionChange> findByExternalReference(String externalReference);

    Optional<TenantSubscriptionChange> findByProviderCheckoutId(String providerCheckoutId);

    Optional<TenantSubscriptionChange> findFirstByTenantIdAndStatusInOrderByCreatedAtDesc(
            UUID tenantId, Collection<BillingSubscriptionChangeStatus> statuses);

    List<TenantSubscriptionChange> findAllByStatusAndEffectiveAtBefore(
            BillingSubscriptionChangeStatus status, LocalDateTime effectiveAt);
}
