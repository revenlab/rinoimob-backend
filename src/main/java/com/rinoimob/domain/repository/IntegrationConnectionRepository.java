package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.IntegrationConnection;
import com.rinoimob.domain.enums.IntegrationProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationConnectionRepository extends JpaRepository<IntegrationConnection, UUID> {
    List<IntegrationConnection> findAllByTenantIdOrderByCreatedAtAsc(UUID tenantId);
    Optional<IntegrationConnection> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<IntegrationConnection> findByInboundKeyAndProvider(String inboundKey, IntegrationProvider provider);
    Optional<IntegrationConnection> findByOutboundKeyAndProvider(String outboundKey, IntegrationProvider provider);
    List<IntegrationConnection> findAllByProviderAndExternalAccountId(IntegrationProvider provider, String externalAccountId);
}
