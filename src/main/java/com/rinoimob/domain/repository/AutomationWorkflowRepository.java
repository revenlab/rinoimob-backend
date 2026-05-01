package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.AutomationWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AutomationWorkflowRepository extends JpaRepository<AutomationWorkflow, UUID> {
    List<AutomationWorkflow> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<AutomationWorkflow> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<AutomationWorkflow> findByTenantIdAndName(UUID tenantId, String name);

    List<AutomationWorkflow> findByTenantIdAndIsActiveTrue(UUID tenantId);

    boolean existsByTenantIdAndName(UUID tenantId, String name);
}
