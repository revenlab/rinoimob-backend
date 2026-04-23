package com.rinoimob.service;

import com.rinoimob.domain.entity.AuditLog;
import com.rinoimob.domain.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

@Service
public class AuditService {

    private static final Logger logger = Logger.getLogger(AuditService.class.getName());
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void log(String tenantId, String userId, String action, String resource, String resourceId, String details) {
        try {
            AuditLog auditLog = new AuditLog(tenantId, userId, action, resource, resourceId, details);
            auditLogRepository.save(auditLog);
            logger.fine("Audit logged: " + action + " on " + resource);
        } catch (Exception e) {
            logger.warning("Failed to log audit: " + action);
        }
    }

    public List<AuditLog> getAuditLog(String tenantId) {
        return auditLogRepository.findByTenantId(tenantId);
    }

    public List<AuditLog> getAuditLog(String tenantId, String userId) {
        return auditLogRepository.findByTenantIdAndUserId(tenantId, userId);
    }

    public List<AuditLog> getAuditLogByAction(String tenantId, String action) {
        return auditLogRepository.findByTenantIdAndAction(tenantId, action);
    }

    public List<AuditLog> getAuditLogByResource(String tenantId, String resource) {
        return auditLogRepository.findByTenantIdAndResource(tenantId, resource);
    }

    public List<AuditLog> getAuditLogBetween(String tenantId, LocalDateTime start, LocalDateTime end) {
        return auditLogRepository.findByTenantIdAndCreatedAtBetween(tenantId, start, end);
    }
}
