package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.EmailSenderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailSenderConfigRepository extends JpaRepository<EmailSenderConfig, UUID> {

    List<EmailSenderConfig> findByTenantIdOrderByCreatedAtAsc(UUID tenantId);

    Optional<EmailSenderConfig> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<EmailSenderConfig> findByTenantIdAndIsDefaultTrue(UUID tenantId);

    boolean existsByTenantIdAndId(UUID tenantId, UUID id);

    @Modifying
    @Query("UPDATE EmailSenderConfig e SET e.isDefault = false WHERE e.tenantId = :tenantId")
    void clearDefaultForTenant(@Param("tenantId") UUID tenantId);
}
