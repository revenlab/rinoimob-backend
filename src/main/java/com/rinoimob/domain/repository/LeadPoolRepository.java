package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.LeadPool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadPoolRepository extends JpaRepository<LeadPool, UUID> {
    List<LeadPool> findByTenantId(UUID tenantId);
    List<LeadPool> findByTenantIdOrderByPriorityAsc(UUID tenantId);
    List<LeadPool> findByTenantIdAndTriggerAfterInactiveDaysIsNotNullOrderByPriorityAsc(UUID tenantId);
    @Query("""
            SELECT p
            FROM LeadPool p
            WHERE p.triggerAfterInactiveDays IS NOT NULL
              AND p.triggerAfterInactiveDays > 0
            ORDER BY p.tenantId ASC, p.priority ASC
            """)
    List<LeadPool> findInactivityPools();
    Optional<LeadPool> findByIdAndTenantId(UUID id, UUID tenantId);
    void deleteByIdAndTenantId(UUID id, UUID tenantId);
}
