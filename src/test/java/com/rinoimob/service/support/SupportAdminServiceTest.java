package com.rinoimob.service.support;

import com.rinoimob.domain.dto.SupportDashboardResponse;
import com.rinoimob.domain.dto.SupportTenantHealthResponse;
import com.rinoimob.domain.dto.SupportTenantSummaryResponse;
import com.rinoimob.domain.dto.SupportUserSummaryResponse;
import com.rinoimob.domain.dto.UpdateSupportTenantRequest;
import com.rinoimob.domain.dto.UpdateSupportUserRequest;
import com.rinoimob.domain.enums.SystemRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test for SupportAdminService orchestrator
 * Tests delegation to sub-services
 */
@ExtendWith(MockitoExtension.class)
class SupportAdminServiceTest {

    @Mock
    private SupportAdminTenantService tenantService;

    @Mock
    private SupportAdminUserService userService;

    @Mock
    private SupportAdminOperatorService operatorService;

    @Mock
    private SupportAdminDashboardService dashboardService;

    @InjectMocks
    private SupportAdminService supportAdminService;

    @Test
    void shouldDelegateListTenantsToTenantService() {
        UUID actorTenantId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        List<SupportTenantSummaryResponse> expectedTenants = List.of();

        when(tenantService.listTenants(actorTenantId, actorUserId))
                .thenReturn(expectedTenants);

        var result = supportAdminService.listTenants(actorTenantId, actorUserId);

        assertThat(result).isEqualTo(expectedTenants);
        verify(tenantService).listTenants(actorTenantId, actorUserId);
    }

    @Test
    void shouldDelegateListOperatorsToOperatorService() {
        UUID actorTenantId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        List<SupportUserSummaryResponse> expectedOperators = List.of();

        when(operatorService.listOperators(actorTenantId, actorUserId))
                .thenReturn(expectedOperators);

        var result = supportAdminService.listOperators(actorTenantId, actorUserId);

        assertThat(result).isEqualTo(expectedOperators);
        verify(operatorService).listOperators(actorTenantId, actorUserId);
    }

    @Test
    void shouldDelegateGetSupportDashboardToDashboardService() {
        UUID actorTenantId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        SupportDashboardResponse expectedDashboard = new SupportDashboardResponse(0, 0, 0, 0, List.of());

        when(dashboardService.getSupportDashboard(actorTenantId, actorUserId))
                .thenReturn(expectedDashboard);

        var result = supportAdminService.getSupportDashboard(actorTenantId, actorUserId);

        assertThat(result).isEqualTo(expectedDashboard);
        verify(dashboardService).getSupportDashboard(actorTenantId, actorUserId);
    }

    @Test
    void shouldDelegateGetTenantHealthToTenantService() {
        UUID actorTenantId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        SupportTenantHealthResponse expectedHealth = new SupportTenantHealthResponse(
                tenantId, "Test", "test", true, "OK", List.of(), 0, 0, 0, 0, "OK", 0, null, null, 0, List.of(), List.of(), List.of()
        );

        when(tenantService.getTenantHealth(actorTenantId, actorUserId, tenantId))
                .thenReturn(expectedHealth);

        var result = supportAdminService.getTenantHealth(actorTenantId, actorUserId, tenantId);

        assertThat(result).isEqualTo(expectedHealth);
        verify(tenantService).getTenantHealth(actorTenantId, actorUserId, tenantId);
    }

    @Test
    void shouldDelegateSetOperatorPermissionsToOperatorService() {
        UUID userId = UUID.randomUUID();
        List<String> permissions = List.of("PERM1", "PERM2");
        List<String> expectedResult = List.of("PERM1", "PERM2");

        when(operatorService.setOperatorPermissions(userId, permissions))
                .thenReturn(expectedResult);

        var result = supportAdminService.setOperatorPermissions(userId, permissions);

        assertThat(result).isEqualTo(expectedResult);
        verify(operatorService).setOperatorPermissions(userId, permissions);
    }

    @Test
    void shouldDelegateListAuditLogsToDashboardService() {
        UUID actorTenantId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDateTime startAt = LocalDateTime.now().minusDays(7);
        LocalDateTime endAt = LocalDateTime.now();
        List<com.rinoimob.domain.dto.SupportAuditLogResponse> expectedLogs = List.of();

        when(dashboardService.listAuditLogs(actorTenantId, actorUserId, tenantId, userId, "ACTION", "RESOURCE", startAt, endAt))
                .thenReturn(expectedLogs);

        var result = supportAdminService.listAuditLogs(actorTenantId, actorUserId, tenantId, userId, "ACTION", "RESOURCE", startAt, endAt);

        assertThat(result).isEqualTo(expectedLogs);
        verify(dashboardService).listAuditLogs(actorTenantId, actorUserId, tenantId, userId, "ACTION", "RESOURCE", startAt, endAt);
    }
}
