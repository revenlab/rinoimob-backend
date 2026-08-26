package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.TenantBillingPayment;
import com.rinoimob.domain.enums.BillingPaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface TenantBillingPaymentRepository extends JpaRepository<TenantBillingPayment, UUID> {

    Optional<TenantBillingPayment> findByProviderPaymentId(String providerPaymentId);

    Page<TenantBillingPayment> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<TenantBillingPayment> findAllByTenantIdAndStatusIn(UUID tenantId,
                                                            Collection<BillingPaymentStatus> statuses,
                                                            Pageable pageable);

    Optional<TenantBillingPayment> findFirstByTenantIdAndStatusInOrderByDueDateAsc(
            UUID tenantId, Collection<BillingPaymentStatus> statuses);
}
