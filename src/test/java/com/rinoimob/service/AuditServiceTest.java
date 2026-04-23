package com.rinoimob.service;

import com.rinoimob.domain.entity.AuditLog;
import com.rinoimob.domain.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AuditServiceTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
    }

    @Test
    void testLogAudit() {
        auditService.log("tenant-1", "user-1", "CREATE", "Property", "prop-1", "Property created");

        List<AuditLog> logs = auditService.getAuditLog("tenant-1");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo("CREATE");
    }

    @Test
    void testGetAuditLogByUser() {
        auditService.log("tenant-1", "user-1", "CREATE", "Property", "prop-1", "Property created");
        auditService.log("tenant-1", "user-2", "UPDATE", "Property", "prop-1", "Property updated");

        List<AuditLog> logs = auditService.getAuditLog("tenant-1", "user-1");
        assertThat(logs).hasSize(1);
    }

    @Test
    void testGetAuditLogByAction() {
        auditService.log("tenant-1", "user-1", "CREATE", "Property", "prop-1", "Property created");
        auditService.log("tenant-1", "user-1", "UPDATE", "Property", "prop-1", "Property updated");

        List<AuditLog> logs = auditService.getAuditLogByAction("tenant-1", "CREATE");
        assertThat(logs).hasSize(1);
    }

    @Test
    void testGetAuditLogByResource() {
        auditService.log("tenant-1", "user-1", "CREATE", "Property", "prop-1", "Property created");
        auditService.log("tenant-1", "user-1", "CREATE", "Tenant", "tenant-1", "Tenant created");

        List<AuditLog> logs = auditService.getAuditLogByResource("tenant-1", "Property");
        assertThat(logs).hasSize(1);
    }

    @Test
    void testGetAuditLogBetween() {
        auditService.log("tenant-1", "user-1", "CREATE", "Property", "prop-1", "Property created");

        LocalDateTime now = LocalDateTime.now();
        List<AuditLog> logs = auditService.getAuditLogBetween("tenant-1", now.minusHours(1), now.plusHours(1));
        assertThat(logs).hasSize(1);
    }
}
