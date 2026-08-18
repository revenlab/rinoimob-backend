package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.LeadPipelineSource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface LeadPipelineSourceRepository extends JpaRepository<LeadPipelineSource, UUID> {
    List<LeadPipelineSource> findByPipelineId(UUID pipelineId);
    Optional<LeadPipelineSource> findBySourceAndPipelineIdIn(String source, Collection<UUID> pipelineIds);
    void deleteByPipelineId(UUID pipelineId);
}
