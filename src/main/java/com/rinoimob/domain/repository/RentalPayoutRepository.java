package com.rinoimob.domain.repository;
import com.rinoimob.domain.entity.RentalPayout;
import com.rinoimob.domain.enums.RentalEnums.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RentalPayoutRepository extends JpaRepository<RentalPayout, UUID> {
    List<RentalPayout> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<RentalPayout> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<RentalPayout> findByProviderTransferId(String providerTransferId);
    Optional<RentalPayout> findByTenantIdAndChargeIdAndOwnerPartyId(UUID tenantId, UUID chargeId, UUID ownerPartyId);
    long countByTenantIdAndStatusIn(UUID tenantId, Collection<PayoutStatus> statuses);
}
