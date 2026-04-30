package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.WhatsappInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WhatsappInstanceRepository extends JpaRepository<WhatsappInstance, UUID> {

    List<WhatsappInstance> findByTenantIdOrderByCreatedAtAsc(UUID tenantId);

    Optional<WhatsappInstance> findByInstanceName(String instanceName);

    Optional<WhatsappInstance> findByIdAndTenantId(UUID id, UUID tenantId);
}
