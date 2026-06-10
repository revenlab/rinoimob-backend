package com.rinoimob.service.billing;

import com.rinoimob.domain.entity.BillingPlan;
import com.rinoimob.domain.entity.TenantSubscription;
import com.rinoimob.domain.enums.BillingPlanCode;
import com.rinoimob.domain.enums.BillingSubscriptionStatus;
import com.rinoimob.domain.repository.BillingPlanRepository;
import com.rinoimob.domain.repository.TenantBillingProfileRepository;
import com.rinoimob.domain.repository.TenantSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BillingPlanResolverService {

    private final BillingPlanRepository billingPlanRepository;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final TenantBillingProfileRepository tenantBillingProfileRepository;

    public BillingPlanResolverService(BillingPlanRepository billingPlanRepository,
                                      TenantSubscriptionRepository tenantSubscriptionRepository,
                                      TenantBillingProfileRepository tenantBillingProfileRepository) {
        this.billingPlanRepository = billingPlanRepository;
        this.tenantSubscriptionRepository = tenantSubscriptionRepository;
        this.tenantBillingProfileRepository = tenantBillingProfileRepository;
    }

    public List<BillingPlan> listCatalogPlans() {
        return billingPlanRepository.findByTenantIdIsNullAndActiveTrueOrderBySortOrderAsc();
    }

    public BillingPlan getRequiredGlobalPlan(BillingPlanCode code) {
        return billingPlanRepository.findByCodeAndTenantIdIsNull(code)
                .orElseThrow(() -> new IllegalArgumentException("Billing plan not found: " + code));
    }

    public BillingPlan resolveEffectivePlan(UUID tenantId) {
        return tenantBillingProfileRepository.findByTenantId(tenantId)
                .map(profile -> profile.getBillingPlan())
                .or(() -> tenantSubscriptionRepository.findByTenantId(tenantId)
                        .filter(subscription -> BillingSubscriptionStatus.ACTIVE.equals(subscription.getStatus())
                                || BillingSubscriptionStatus.TRIAL.equals(subscription.getStatus()))
                        .map(TenantSubscription::getBillingPlan))
                .orElseGet(() -> getRequiredGlobalPlan(BillingPlanCode.FREE));
    }
}
