package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    Page<Task> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);
    Page<Task> findByTenantIdAndLeadIdAndDeletedAtIsNull(UUID tenantId, UUID leadId, Pageable pageable);
    Page<Task> findByTenantIdAndAssignedToAndDeletedAtIsNull(UUID tenantId, UUID assignedTo, Pageable pageable);
    Page<Task> findByTenantIdAndCompletedAtIsNullAndDeletedAtIsNull(UUID tenantId, Pageable pageable);
    Page<Task> findByTenantIdAndCompletedAtIsNotNullAndDeletedAtIsNull(UUID tenantId, Pageable pageable);
    List<Task> findByTenantIdAndLeadIdAndDeletedAtIsNull(UUID tenantId, UUID leadId);
    long countByTenantIdAndCompletedAtIsNullAndDeletedAtIsNull(UUID tenantId);
    long countByTenantIdAndDueAtBeforeAndCompletedAtIsNullAndDeletedAtIsNull(UUID tenantId, LocalDateTime now);
}
