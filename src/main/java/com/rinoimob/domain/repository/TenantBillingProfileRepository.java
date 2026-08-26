package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.TenantBillingProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface TenantBillingProfileRepository extends JpaRepository<TenantBillingProfile, UUID> {

    Optional<TenantBillingProfile> findByTenantId(UUID tenantId);

    Optional<TenantBillingProfile> findByProviderCustomerId(String providerCustomerId);

    List<TenantBillingProfile> findAllByProviderCustomerIdIsNotNull();
}
