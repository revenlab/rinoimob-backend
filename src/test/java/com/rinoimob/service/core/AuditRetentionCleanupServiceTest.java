package com.rinoimob.service.core;

import com.rinoimob.domain.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditRetentionCleanupServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditRetentionCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        cleanupService = new AuditRetentionCleanupService(auditLogRepository, 30);
    }

    @Test
    void shouldDeleteAuditLogsOlderThanConfiguredRetention() {
        when(auditLogRepository.deleteAllByCreatedAtBefore(any())).thenReturn(7);

        LocalDateTime beforeCall = LocalDateTime.now();
        int deleted = cleanupService.cleanupExpiredAuditLogs();

        assertThat(deleted).isEqualTo(7);

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditLogRepository).deleteAllByCreatedAtBefore(cutoffCaptor.capture());
        LocalDateTime cutoff = cutoffCaptor.getValue();
        assertThat(cutoff).isAfter(beforeCall.minusDays(31));
        assertThat(cutoff).isBefore(beforeCall.minusDays(29));
    }

    @Test
    void shouldSkipCleanupWhenRetentionIsNotPositive() {
        cleanupService = new AuditRetentionCleanupService(auditLogRepository, 0);

        int deleted = cleanupService.cleanupExpiredAuditLogs();

        assertThat(deleted).isZero();
        verify(auditLogRepository, never()).deleteAllByCreatedAtBefore(any());
    }
}
