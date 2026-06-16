package com.rinoimob.service.billing;

import com.rinoimob.domain.entity.BillingPlan;
import com.rinoimob.domain.entity.TenantSubscription;
import com.rinoimob.domain.enums.BillingPlanCode;
import com.rinoimob.domain.enums.BillingProvider;
import com.rinoimob.domain.enums.BillingSubscriptionStatus;
import com.rinoimob.domain.repository.TenantSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class TenantSubscriptionService {

    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final BillingPlanResolverService billingPlanResolverService;

    public TenantSubscriptionService(TenantSubscriptionRepository tenantSubscriptionRepository,
                                     BillingPlanResolverService billingPlanResolverService) {
        this.tenantSubscriptionRepository = tenantSubscriptionRepository;
        this.billingPlanResolverService = billingPlanResolverService;
    }

    @Transactional(readOnly = true)
    public Optional<TenantSubscription> findByTenantId(UUID tenantId) {
        return tenantSubscriptionRepository.findByTenantId(tenantId);
    }

    @Transactional
    public TenantSubscription ensureFreeSubscriptionForTenant(UUID tenantId) {
        Optional<TenantSubscription> existing = tenantSubscriptionRepository.findByTenantId(tenantId);
        if (existing.isPresent()) {
            return existing.get();
        }

        BillingPlan freePlan = billingPlanResolverService.getRequiredGlobalPlan(BillingPlanCode.FREE);
        LocalDateTime now = LocalDateTime.now();

        TenantSubscription subscription = new TenantSubscription();
        subscription.setTenantId(tenantId);
        subscription.setBillingPlan(freePlan);
        subscription.setStatus(BillingSubscriptionStatus.ACTIVE);
        subscription.setProvider(BillingProvider.MANUAL);
        subscription.setCurrentPeriodStart(now);
        subscription.setStartedAt(now);
        subscription.setLastPlanChangeAt(now);
        subscription.setCancelAtPeriodEnd(false);

        return tenantSubscriptionRepository.save(subscription);
    }
}
