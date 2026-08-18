package com.rinoimob.service.billing;

import com.rinoimob.domain.entity.BillingPlan;
import com.rinoimob.domain.entity.TenantSubscription;
import com.rinoimob.domain.enums.BillingPlanCode;
import com.rinoimob.domain.enums.BillingProvider;
import com.rinoimob.domain.enums.BillingSubscriptionStatus;
import com.rinoimob.domain.repository.TenantSubscriptionRepository;
import com.rinoimob.service.billing.payment.BillingGatewayPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PastDueSubscriptionScheduler {

    private static final Logger log = LoggerFactory.getLogger(PastDueSubscriptionScheduler.class);
    private static final int GRACE_PERIOD_DAYS = 7;

    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final BillingPlanResolverService billingPlanResolverService;
    private final TenantBillingProfileService tenantBillingProfileService;
    private final BillingGatewayPort billingGatewayPort;

    public PastDueSubscriptionScheduler(TenantSubscriptionRepository tenantSubscriptionRepository,
                                        BillingPlanResolverService billingPlanResolverService,
                                        TenantBillingProfileService tenantBillingProfileService,
                                        BillingGatewayPort billingGatewayPort) {
        this.tenantSubscriptionRepository = tenantSubscriptionRepository;
        this.billingPlanResolverService = billingPlanResolverService;
        this.tenantBillingProfileService = tenantBillingProfileService;
        this.billingGatewayPort = billingGatewayPort;
    }

    @Scheduled(fixedDelayString = "${billing.asaas.past-due-scan-interval-ms:3600000}")
    @Transactional
    public void downgradeExpiredPastDueSubscriptions() {
        downgradeSubscriptionsOverdueForMoreThanSevenDays();
    }

    @Transactional
    public int downgradeSubscriptionsOverdueForMoreThanSevenDays() {
        LocalDateTime now = LocalDateTime.now();
        List<TenantSubscription> overdueSubscriptions = tenantSubscriptionRepository.findAllByStatusAndPastDueAtBefore(
                BillingSubscriptionStatus.PAST_DUE,
                now.minusDays(GRACE_PERIOD_DAYS)
        );
        BillingPlan freePlan = billingPlanResolverService.getRequiredGlobalPlan(BillingPlanCode.FREE);
        int downgraded = 0;
        for (TenantSubscription subscription : overdueSubscriptions) {
            if (!cancelAsaasSubscriptionIfNeeded(subscription)) {
                continue;
            }
            subscription.setBillingPlan(freePlan);
            subscription.setProvider(BillingProvider.MANUAL);
            subscription.setStatus(BillingSubscriptionStatus.CANCELED);
            subscription.setProviderCustomerId(null);
            subscription.setProviderSubscriptionId(null);
            subscription.setProviderCheckoutId(null);
            subscription.setCurrentPeriodEnd(now);
            subscription.setEndedAt(now);
            subscription.setPastDueAt(null);
            subscription.setLastPlanChangeAt(now);
            subscription.setCancelAtPeriodEnd(true);
            tenantSubscriptionRepository.save(subscription);
            tenantBillingProfileService.replaceProfileForPlan(
                    subscription.getTenantId(), freePlan, null, "Downgraded to Free after more than 7 days past due"
            );
            downgraded++;
        }
        return downgraded;
    }

    private boolean cancelAsaasSubscriptionIfNeeded(TenantSubscription subscription) {
        if (subscription.getProvider() != BillingProvider.ASAAS
                || subscription.getProviderSubscriptionId() == null
                || subscription.getProviderSubscriptionId().isBlank()) {
            return true;
        }
        try {
            billingGatewayPort.cancelSubscription(subscription.getProviderSubscriptionId());
            return true;
        } catch (IllegalStateException | RestClientResponseException exception) {
            log.warn("Could not cancel overdue Asaas subscription {} for tenant {}. Will retry later.",
                    subscription.getProviderSubscriptionId(), subscription.getTenantId(), exception);
            return false;
        }
    }
}
