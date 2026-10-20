package com.rinoimob.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.api.client.MetaLeadAdsClient;
import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.entity.*;
import com.rinoimob.domain.enums.*;
import com.rinoimob.domain.repository.*;
import com.rinoimob.service.billing.TenantPlanAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MetaLeadReconciliationServiceTest {
    private final IntegrationRouteRepository routes = mock(IntegrationRouteRepository.class);
    private final IntegrationConnectionRepository connections = mock(IntegrationConnectionRepository.class);
    private final IntegrationInboxService inbox = mock(IntegrationInboxService.class);
    private final TenantPlanAccessService plans = mock(TenantPlanAccessService.class);
    private final IntegrationSecretService secrets = new IntegrationSecretService("a-stable-test-key-with-more-than-32-characters");
    private final MetaLeadAdsClient meta = mock(MetaLeadAdsClient.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final MetaLeadReconciliationService service = new MetaLeadReconciliationService(routes, connections,
            inbox, plans, secrets, meta, new ObjectMapper(), transactions);
    private final Instant now = Instant.parse("2026-10-20T22:00:00Z");
    private final UUID tenant = UUID.randomUUID();
    private final IntegrationRoute route = new IntegrationRoute();
    private final IntegrationConnection connection = new IntegrationConnection();

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(now, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "routeTimeZone", ZoneOffset.UTC);
        route.setId(UUID.randomUUID());
        route.setTenantId(tenant);
        route.setConnectionId(UUID.randomUUID());
        route.setExternalResourceId("123");
        route.setCreatedAt(LocalDateTime.ofInstant(now.minusSeconds(7200), ZoneOffset.UTC));
        connection.setId(route.getConnectionId());
        connection.setTenantId(tenant);
        connection.setProvider(IntegrationProvider.META_LEAD_ADS);
        connection.setStatus(IntegrationConnectionStatus.ACTIVE);
        connection.setEncryptedCredentials(secrets.encrypt("{\"pageAccessToken\":\"page-token\"}"));
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(routes.lockDueMetaSyncRoute(tenant, route.getId(), now)).thenReturn(Optional.of(route));
        when(connections.findByIdAndTenantId(connection.getId(), tenant)).thenReturn(Optional.of(connection));
        when(plans.isEnabled(tenant, BillingFeature.INTEGRATIONS)).thenReturn(true);
    }

    @Test
    void shouldRecoverFromRouteCreationAndCommitCheckpointOnlyWhenWindowIsComplete() {
        var lead = new MetaLeadAdsClient.LeadReference("lead", "123", now.minusSeconds(120));
        when(meta.listFormLeads(eq("123"), eq("page-token"), any(), any(), isNull()))
                .thenReturn(new MetaLeadAdsClient.LeadPage(List.of(lead), null));
        when(inbox.recoverMetaLead(tenant, connection.getId(), lead)).thenReturn(1);
        service.reconcile(tenant, route.getId());
        verify(meta).listFormLeads("123", "page-token", now.minusSeconds(7200), now.minusSeconds(60), null);
        assertThat(route.getMetaSyncedUntil()).isEqualTo(now.minusSeconds(60));
        assertThat(route.getMetaLastSyncAt()).isEqualTo(now);
        assertThat(route.getMetaRecoveredCount()).isEqualTo(1);
        assertThat(route.getMetaSyncCursor()).isNull();
        assertThat(route.getMetaNextSyncAt()).isEqualTo(now.plusSeconds(300));
    }

    @Test
    void shouldPersistCursorAndResumeTheSameWindowBeforeAdvancingCheckpoint() {
        when(meta.listFormLeads(anyString(), anyString(), any(), any(), isNull()))
                .thenReturn(new MetaLeadAdsClient.LeadPage(List.of(), "cursor-2"));
        service.reconcile(tenant, route.getId());
        assertThat(route.getMetaSyncedUntil()).isNull();
        assertThat(route.getMetaSyncCursor()).isEqualTo("cursor-2");
        Instant from = route.getMetaSyncFrom();
        Instant until = route.getMetaSyncUntil();
        when(meta.listFormLeads("123", "page-token", from, until, "cursor-2"))
                .thenReturn(new MetaLeadAdsClient.LeadPage(List.of(), null));
        service.reconcile(tenant, route.getId());
        assertThat(route.getMetaSyncedUntil()).isEqualTo(until);
        assertThat(route.getMetaSyncFrom()).isNull();
    }

    @Test
    void shouldOverlapPreviouslyCheckedPeriodWithoutGoingBeforeRouteCreation() {
        route.setMetaSyncedUntil(now.minusSeconds(1800));
        when(meta.listFormLeads(anyString(), anyString(), any(), any(), isNull()))
                .thenReturn(new MetaLeadAdsClient.LeadPage(List.of(), null));
        service.reconcile(tenant, route.getId());
        verify(meta).listFormLeads("123", "page-token", now.minusSeconds(5400), now.minusSeconds(60), null);
    }

    @Test
    void shouldKeepCheckpointOnProviderFailureAndBackOffWithoutExposingSecrets() {
        route.setMetaSyncedUntil(now.minusSeconds(1800));
        when(meta.listFormLeads(anyString(), anyString(), any(), any(), isNull()))
                .thenThrow(new IllegalStateException("secret-page-token"));
        service.reconcile(tenant, route.getId());
        assertThat(route.getMetaSyncedUntil()).isEqualTo(now.minusSeconds(1800));
        assertThat(route.getMetaSyncError()).doesNotContain("secret-page-token");
        assertThat(route.getMetaNextSyncAt()).isEqualTo(now.plusSeconds(60));
        verify(transactions).rollback(any());
    }

    @Test
    void shouldRestartExpiredCursorWithoutLosingItsWindow() {
        Instant from = now.minusSeconds(600);
        route.setMetaSyncFrom(from);
        route.setMetaSyncUntil(now.minusSeconds(300));
        route.setMetaSyncCursor("expired");
        when(meta.listFormLeads(anyString(), anyString(), any(), any(), eq("expired")))
                .thenThrow(new MetaLeadAdsClient.LeadReadException(false, true));
        service.reconcile(tenant, route.getId());
        assertThat(route.getMetaSyncCursor()).isNull();
        assertThat(route.getMetaSyncFrom()).isEqualTo(from);
        assertThat(route.getMetaSyncedUntil()).isNull();
    }

    @Test
    void shouldStopFrequentRequestsUntilAuthorizationIsFixed() {
        when(meta.listFormLeads(anyString(), anyString(), any(), any(), isNull()))
                .thenThrow(new MetaLeadAdsClient.LeadReadException(true, false));
        service.reconcile(tenant, route.getId());
        assertThat(route.getMetaSyncError()).contains("Reconecte");
        assertThat(route.getMetaNextSyncAt()).isEqualTo(now.plusSeconds(3600));
    }

    @Test
    void shouldSkipPausedConnectionsAndTenantsWithoutFeature() {
        connection.setStatus(IntegrationConnectionStatus.PAUSED);
        service.reconcile(tenant, route.getId());
        verifyNoInteractions(meta, inbox);
        connection.setStatus(IntegrationConnectionStatus.ACTIVE);
        when(plans.isEnabled(tenant, BillingFeature.INTEGRATIONS)).thenReturn(false);
        service.reconcile(tenant, route.getId());
        verifyNoInteractions(meta, inbox);
        assertThat(route.getMetaNextSyncAt()).isEqualTo(now.plusSeconds(3600));
    }

    @Test
    void shouldNotReadAnotherTenantsRouteAndShouldRestoreContext() {
        UUID otherTenant = UUID.randomUUID();
        TenantContext.setTenantId("original");
        service.reconcile(otherTenant, route.getId());
        verifyNoInteractions(meta, inbox);
        assertThat(TenantContext.getTenantId()).isEqualTo("original");
        TenantContext.clear();
    }

    @Test
    void shouldBoundLongDowntimeToOneDayPerWindow() {
        route.setCreatedAt(LocalDateTime.ofInstant(now.minusSeconds(86400 * 10), ZoneOffset.UTC));
        when(meta.listFormLeads(anyString(), anyString(), any(), any(), isNull()))
                .thenReturn(new MetaLeadAdsClient.LeadPage(List.of(), null));
        service.reconcile(tenant, route.getId());
        verify(meta).listFormLeads("123", "page-token", now.minusSeconds(86400 * 10), now.minusSeconds(86400 * 9), null);
        assertThat(route.getMetaNextSyncAt()).isEqualTo(now.plusSeconds(1));
    }
}
