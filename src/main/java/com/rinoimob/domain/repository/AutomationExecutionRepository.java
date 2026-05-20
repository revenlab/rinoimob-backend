package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.AutomationExecution;
import com.rinoimob.domain.enums.WorkflowExecutionStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AutomationExecutionRepository extends JpaRepository<AutomationExecution, UUID> {
    List<AutomationExecution> findByWorkflowIdOrderByCreatedAtDesc(UUID workflowId);

    List<AutomationExecution> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<AutomationExecution> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, WorkflowExecutionStatus status);

    List<AutomationExecution> findByStatusOrderByCreatedAtDesc(WorkflowExecutionStatus status);

    List<AutomationExecution> findByStatusAndResumeAtLessThanEqualOrderByResumeAtAsc(
            WorkflowExecutionStatus status, LocalDateTime resumeAt);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM automation_executions
                WHERE workflow_id = :workflowId
                  AND trigger_event = :triggerEvent
                  AND trigger_data ->> 'leadId' = :leadId
                  AND created_at > :createdAfter
            )
            """, nativeQuery = true)
    boolean existsLeadTriggerExecutionAfter(
            @Param("workflowId") UUID workflowId,
            @Param("triggerEvent") String triggerEvent,
            @Param("leadId") String leadId,
            @Param("createdAfter") LocalDateTime createdAfter);
}
