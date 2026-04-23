package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    List<AuditLog> findByTenantId(String tenantId);

    List<AuditLog> findByTenantIdAndActorId(String tenantId, String actorId);

    List<AuditLog> findByTenantIdAndResourceType(String tenantId, String resourceType);

    List<AuditLog> findByTenantIdAndCreatedAtBetween(String tenantId, LocalDateTime startDate, LocalDateTime endDate);

}
