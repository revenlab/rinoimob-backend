package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.AutomationExecution;
import com.rinoimob.domain.enums.WorkflowExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AutomationExecutionRepository extends JpaRepository<AutomationExecution, UUID> {
    List<AutomationExecution> findByWorkflowIdOrderByCreatedAtDesc(UUID workflowId);

    List<AutomationExecution> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<AutomationExecution> findByStatusOrderByCreatedAtDesc(WorkflowExecutionStatus status);
}
