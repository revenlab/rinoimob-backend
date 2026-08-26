package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.TenantSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import com.rinoimob.domain.enums.BillingSubscriptionStatus;

@Repository
public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, UUID> {

    Optional<TenantSubscription> findByTenantId(UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select subscription from TenantSubscription subscription where subscription.tenantId = :tenantId")
    Optional<TenantSubscription> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);

    Optional<TenantSubscription> findByProviderSubscriptionId(String providerSubscriptionId);

    Optional<TenantSubscription> findByProviderCheckoutId(String providerCheckoutId);

    List<TenantSubscription> findAllByProviderCustomerId(String providerCustomerId);

    List<TenantSubscription> findAllByStatusAndPastDueAtBefore(BillingSubscriptionStatus status, LocalDateTime cutoff);

    List<TenantSubscription> findAllByCancelAtPeriodEndTrueAndCurrentPeriodEndBefore(LocalDateTime cutoff);
}
