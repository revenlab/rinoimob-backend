package com.rinoimob.service.core;

import com.rinoimob.domain.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.logging.Logger;

@Service
public class AuditRetentionCleanupService {

    private static final Logger logger = Logger.getLogger(AuditRetentionCleanupService.class.getName());

    private final AuditLogRepository auditLogRepository;
    private final int retentionDays;

    public AuditRetentionCleanupService(AuditLogRepository auditLogRepository,
                                        @Value("${audit.retention-days:180}") int retentionDays) {
        this.auditLogRepository = auditLogRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(fixedDelayString = "${audit.retention.cleanup-interval-ms:86400000}")
    @Transactional
    public void runCleanup() {
        cleanupExpiredAuditLogs();
    }

    @Transactional
    public int cleanupExpiredAuditLogs() {
        if (retentionDays <= 0) {
            logger.warning("Skipping audit log cleanup because retentionDays is not positive: " + retentionDays);
            return 0;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = auditLogRepository.deleteAllByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            logger.info("Deleted " + deleted + " audit logs older than " + cutoff);
        }
        return deleted;
    }
}
