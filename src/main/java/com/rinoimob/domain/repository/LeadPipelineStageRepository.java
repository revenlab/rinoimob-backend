package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.LeadPipelineStage;
import com.rinoimob.domain.enums.LeadPipelineStageKind;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface LeadPipelineStageRepository extends JpaRepository<LeadPipelineStage, UUID> {
    List<LeadPipelineStage> findByPipelineIdOrderByPositionAsc(UUID pipelineId);
    Optional<LeadPipelineStage> findByIdAndPipelineId(UUID id, UUID pipelineId);
    Optional<LeadPipelineStage> findFirstByPipelineIdAndKindOrderByPositionAsc(UUID pipelineId, LeadPipelineStageKind kind);
}
