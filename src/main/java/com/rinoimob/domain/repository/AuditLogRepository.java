package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByTenantId(String tenantId);

    List<AuditLog> findByTenantIdAndUserId(String tenantId, String userId);

    List<AuditLog> findByTenantIdAndAction(String tenantId, String action);

    List<AuditLog> findByTenantIdAndResource(String tenantId, String resource);

    List<AuditLog> findByTenantIdAndCreatedAtBetween(String tenantId, LocalDateTime start, LocalDateTime end);

    List<AuditLog> findAllByOrderByCreatedAtDesc();

    List<AuditLog> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    @Query(value = "SELECT * FROM audit_logs WHERE user_id = CAST(:userId AS text) ORDER BY created_at DESC LIMIT 10", nativeQuery = true)
    List<AuditLog> findTop10ByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from AuditLog auditLog where auditLog.createdAt < :cutoff")
    int deleteAllByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
