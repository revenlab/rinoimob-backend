package com.rinoimob.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.api.client.MetaLeadAdsClient;
import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.entity.IntegrationRoute;
import com.rinoimob.domain.enums.BillingFeature;
import com.rinoimob.domain.enums.IntegrationConnectionStatus;
import com.rinoimob.domain.enums.IntegrationProvider;
import com.rinoimob.domain.repository.IntegrationConnectionRepository;
import com.rinoimob.domain.repository.IntegrationRouteRepository;
import com.rinoimob.service.billing.TenantPlanAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetaLeadReconciliationService {
    private final IntegrationRouteRepository routeRepository;
    private final IntegrationConnectionRepository connectionRepository;
    private final IntegrationInboxService inboxService;
    private final TenantPlanAccessService planAccessService;
    private final IntegrationSecretService secretService;
    private final MetaLeadAdsClient metaClient;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private Clock clock = Clock.systemUTC();
    private ZoneId routeTimeZone = ZoneId.systemDefault();

    @Value("${integrations.meta.sync.enabled:true}")
    private boolean enabled = true;
    @Value("${integrations.meta.sync.interval-seconds:300}")
    private long intervalSeconds = 300;
    @Value("${integrations.meta.sync.overlap-seconds:3600}")
    private long overlapSeconds = 3600;
    @Value("${integrations.meta.sync.settle-seconds:60}")
    private long settleSeconds = 60;

    @Scheduled(fixedDelayString = "${integrations.meta.sync.poll-interval-ms:30000}",
            initialDelayString = "${integrations.meta.sync.initial-delay-ms:15000}")
    public void reconcilePending() {
        if (!enabled) return;
        for (IntegrationRouteRepository.SyncTarget target : routeRepository.findDueMetaSyncTargets(clock.instant())) {
            reconcile(target.getTenantId(), target.getId());
        }
    }

    public void reconcile(UUID tenantId, UUID routeId) {
        String previousTenant = TenantContext.getTenantId();
        UUID previousUser = TenantContext.getUserId();
        try {
            TenantContext.clear();
            TenantContext.setTenantId(tenantId.toString());
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setTimeout(60);
            transaction.executeWithoutResult(status -> reconcilePage(tenantId, routeId));
        } catch (Exception exception) {
            // Provider exceptions may contain request URLs; never log their message or cause.
            log.warn("Meta reconciliation failed tenant={} route={} type={}", tenantId, routeId,
                    exception.getClass().getSimpleName());
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> recordFailure(tenantId, routeId, exception));
            } catch (Exception persistenceFailure) {
                log.error("Could not record Meta reconciliation failure tenant={} route={} type={}",
                        tenantId, routeId, persistenceFailure.getClass().getSimpleName());
            }
        } finally {
            TenantContext.clear();
            if (previousTenant != null) TenantContext.setTenantId(previousTenant);
            if (previousUser != null) TenantContext.setUserId(previousUser);
        }
    }

    private void reconcilePage(UUID tenantId, UUID routeId) {
        Instant now = clock.instant();
        IntegrationRoute route = routeRepository.lockDueMetaSyncRoute(tenantId, routeId, now).orElse(null);
        if (route == null) return; // Another worker owns this row, or it is no longer due/active.
        var connection = connectionRepository.findByIdAndTenantId(route.getConnectionId(), tenantId)
                .filter(item -> item.getProvider() == IntegrationProvider.META_LEAD_ADS
                        && item.getStatus() == IntegrationConnectionStatus.ACTIVE).orElse(null);
        if (connection == null) return;
        if (!planAccessService.isEnabled(tenantId, BillingFeature.INTEGRATIONS)) {
            route.setMetaSyncError("Integrações não estão habilitadas no plano atual.");
            route.setMetaNextSyncAt(now.plusSeconds(3600));
            routeRepository.save(route);
            return;
        }
        Instant baseline = route.getCreatedAt().atZone(routeTimeZone).toInstant().truncatedTo(ChronoUnit.SECONDS);
        if (route.getMetaSyncFrom() == null) {
            Instant from = route.getMetaSyncedUntil() == null ? baseline
                    : route.getMetaSyncedUntil().minusSeconds(Math.max(0, overlapSeconds));
            if (from.isBefore(baseline)) from = baseline;
            Instant until = now.minusSeconds(Math.max(0, settleSeconds)).truncatedTo(ChronoUnit.SECONDS);
            // Bound backlog work while retaining the exact unfinished window across restarts.
            Instant maxUntil = from.plusSeconds(86400);
            if (until.isAfter(maxUntil)) until = maxUntil;
            if (until.isBefore(from)) {
                route.setMetaNextSyncAt(now.plusSeconds(Math.max(1, intervalSeconds)));
                routeRepository.save(route);
                return;
            }
            route.setMetaSyncFrom(from);
            route.setMetaSyncUntil(until);
            route.setMetaSyncCursor(null);
        }
        String token;
        try {
            token = objectMapper.readTree(secretService.decrypt(connection.getEncryptedCredentials()))
                    .path("pageAccessToken").asText();
        } catch (Exception exception) {
            throw new IllegalStateException("Meta credentials are not available");
        }
        if (token.isBlank()) throw new IllegalStateException("Meta Page authorization is incomplete");
        var page = metaClient.listFormLeads(route.getExternalResourceId(), token,
                route.getMetaSyncFrom(), route.getMetaSyncUntil(), route.getMetaSyncCursor());
        long recovered = 0;
        for (var lead : page.leads()) {
            if (!route.getExternalResourceId().equals(lead.formId())
                    || lead.createdAt().isBefore(route.getMetaSyncFrom())
                    || lead.createdAt().isAfter(route.getMetaSyncUntil())) {
                throw new IllegalStateException("Meta returned a lead outside the requested form/window");
            }
            recovered += inboxService.recoverMetaLead(tenantId, connection.getId(), lead);
        }
        route.setMetaRecoveredCount(route.getMetaRecoveredCount() + recovered);
        route.setMetaSyncFailures(0);
        route.setMetaSyncError(null);
        if (page.nextCursor() != null) {
            if (page.nextCursor().equals(route.getMetaSyncCursor())) throw new IllegalStateException("Meta lead cursor did not advance");
            route.setMetaSyncCursor(page.nextCursor());
            route.setMetaNextSyncAt(now.plusSeconds(1));
        } else {
            route.setMetaSyncedUntil(route.getMetaSyncUntil());
            route.setMetaLastSyncAt(now);
            boolean backlog = route.getMetaSyncUntil().isBefore(now.minusSeconds(Math.max(0, settleSeconds) + 1));
            route.setMetaSyncFrom(null);
            route.setMetaSyncUntil(null);
            route.setMetaSyncCursor(null);
            route.setMetaNextSyncAt(now.plusSeconds(backlog ? 1 : Math.max(1, intervalSeconds)));
        }
        routeRepository.save(route);
    }

    private void recordFailure(UUID tenantId, UUID routeId, Exception exception) {
        Instant now = clock.instant();
        var route = routeRepository.lockDueMetaSyncRoute(tenantId, routeId, now).orElse(null);
        if (route == null) return;
        int failures = Math.min(30, route.getMetaSyncFailures() + 1);
        route.setMetaSyncFailures(failures);
        route.setMetaNextSyncAt(now.plusSeconds(Math.min(3600, 60L << Math.min(failures - 1, 6))));
        route.setMetaSyncError("Falha ao conferir leads na Meta. Uma nova tentativa será feita automaticamente.");
        if (exception instanceof MetaLeadAdsClient.LeadReadException providerFailure) {
            route.setMetaSyncError(providerFailure.getMessage());
            if (providerFailure.isAuthorizationFailure()) route.setMetaNextSyncAt(now.plusSeconds(3600));
            if (providerFailure.isInvalidCursor()) route.setMetaSyncCursor(null);
        }
        // Keep the previous complete checkpoint and unfinished window, including after partial failures.
        routeRepository.save(route);
    }
}
