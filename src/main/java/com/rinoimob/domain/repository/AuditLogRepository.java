package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByTenantId(String tenantId);

    List<AuditLog> findByTenantIdAndUserId(String tenantId, String userId);

    List<AuditLog> findByTenantIdAndAction(String tenantId, String action);
    
    List<AuditLog> findByTenantIdAndActorId(String tenantId, String actorId);

    List<AuditLog> findByTenantIdAndResource(String tenantId, String resource);

    List<AuditLog> findByTenantIdAndCreatedAtBetween(String tenantId, LocalDateTime start, LocalDateTime end);
}
