package com.rinoimob.service;

import com.rinoimob.domain.entity.AuditLog;
import com.rinoimob.domain.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    private AuditService auditService;

    @Mock
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditLogRepository);
    }

    @Test
    void testLogAudit() {
        AuditLog savedLog = new AuditLog("tenant-1", "user-1", "CREATE", "Property", "prop-1", "Property created");
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedLog);
        when(auditLogRepository.findByTenantId("tenant-1")).thenReturn(List.of(savedLog));

        auditService.log("tenant-1", "user-1", "CREATE", "Property", "prop-1", "Property created");

        List<AuditLog> logs = auditService.getAuditLog("tenant-1");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo("CREATE");
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void testGetAuditLogByUser() {
        AuditLog log = new AuditLog("tenant-1", "user-1", "CREATE", "Property", "prop-1", "Property created");
        when(auditLogRepository.findByTenantIdAndUserId("tenant-1", "user-1")).thenReturn(List.of(log));

        List<AuditLog> logs = auditService.getAuditLog("tenant-1", "user-1");
        assertThat(logs).hasSize(1);
    }

    @Test
    void testGetAuditLogByAction() {
        AuditLog log = new AuditLog("tenant-1", "user-1", "CREATE", "Property", "prop-1", "Property created");
        when(auditLogRepository.findByTenantIdAndAction("tenant-1", "CREATE")).thenReturn(List.of(log));

        List<AuditLog> logs = auditService.getAuditLogByAction("tenant-1", "CREATE");
        assertThat(logs).hasSize(1);
    }

    @Test
    void testGetAuditLogByResource() {
        AuditLog log = new AuditLog("tenant-1", "user-1", "CREATE", "Property", "prop-1", "Property created");
        when(auditLogRepository.findByTenantIdAndResource("tenant-1", "Property")).thenReturn(List.of(log));

        List<AuditLog> logs = auditService.getAuditLogByResource("tenant-1", "Property");
        assertThat(logs).hasSize(1);
    }

    @Test
    void testGetAuditLogBetween() {
        AuditLog log = new AuditLog("tenant-1", "user-1", "CREATE", "Property", "prop-1", "Property created");

        LocalDateTime now = LocalDateTime.now();
        when(auditLogRepository.findByTenantIdAndCreatedAtBetween("tenant-1", now.minusHours(1), now.plusHours(1)))
                .thenReturn(List.of(log));

        List<AuditLog> logs = auditService.getAuditLogBetween("tenant-1", now.minusHours(1), now.plusHours(1));
        assertThat(logs).hasSize(1);
    }
}
