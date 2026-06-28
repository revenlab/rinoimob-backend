package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskTypeRepository extends JpaRepository<TaskType, UUID> {

    @Query("SELECT t FROM TaskType t WHERE (t.tenantId IS NULL OR t.tenantId = :tenantId) AND t.active = true ORDER BY t.position ASC, t.name ASC")
    List<TaskType> findAvailableForTenant(@Param("tenantId") UUID tenantId);

    List<TaskType> findByTenantIdAndActiveTrue(UUID tenantId);

    @Query("SELECT t FROM TaskType t WHERE t.id = :id AND (t.tenantId IS NULL OR t.tenantId = :tenantId) AND t.active = true")
    Optional<TaskType> findAvailableByIdForTenant(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
