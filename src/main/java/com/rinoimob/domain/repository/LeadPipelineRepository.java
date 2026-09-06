package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.Lead;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public interface LeadPipelineRepository extends Repository<Lead, UUID> {

    @Query(value = "SELECT id FROM lead_pipelines WHERE tenant_id = :tenantId AND is_default = true AND archived_at IS NULL", nativeQuery = true)
    Optional<UUID> findDefaultPipelineId(UUID tenantId);

    @Query(value = "SELECT id FROM lead_pipeline_stages WHERE pipeline_id = :pipelineId AND kind = 'OPEN' ORDER BY position LIMIT 1", nativeQuery = true)
    Optional<UUID> findInitialOpenStageId(UUID pipelineId);
}
