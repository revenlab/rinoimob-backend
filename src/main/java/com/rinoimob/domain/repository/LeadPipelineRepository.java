package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.LeadPipeline;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface LeadPipelineRepository extends JpaRepository<LeadPipeline, UUID> {
    List<LeadPipeline> findByTenantIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID tenantId);
    Optional<LeadPipeline> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<LeadPipeline> findByTenantIdAndDefaultPipelineTrueAndArchivedAtIsNull(UUID tenantId);
}
