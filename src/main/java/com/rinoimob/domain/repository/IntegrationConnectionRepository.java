package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.IntegrationConnection;
import com.rinoimob.domain.enums.IntegrationProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationConnectionRepository extends JpaRepository<IntegrationConnection, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from IntegrationConnection c where c.id = :id and c.tenantId = :tenantId")
    Optional<IntegrationConnection> findForOAuthByIdAndTenantId(UUID id, UUID tenantId);
    List<IntegrationConnection> findAllByTenantIdOrderByCreatedAtAsc(UUID tenantId);
    Optional<IntegrationConnection> findByIdAndTenantId(UUID id, UUID tenantId);
    boolean existsByTenantIdAndProviderAndExternalAccountIdAndIdNot(UUID tenantId, IntegrationProvider provider, String externalAccountId, UUID id);
    Optional<IntegrationConnection> findByInboundKeyAndProvider(String inboundKey, IntegrationProvider provider);
    Optional<IntegrationConnection> findByOutboundKeyAndProvider(String outboundKey, IntegrationProvider provider);
    List<IntegrationConnection> findAllByProviderAndExternalAccountId(IntegrationProvider provider, String externalAccountId);
}
