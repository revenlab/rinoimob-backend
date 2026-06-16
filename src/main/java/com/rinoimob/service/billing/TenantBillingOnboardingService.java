package com.rinoimob.service.billing;

import com.rinoimob.domain.entity.TenantSubscription;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenantBillingOnboardingService {

    private final TenantSubscriptionService tenantSubscriptionService;
    private final TenantBillingProfileService tenantBillingProfileService;

    public TenantBillingOnboardingService(TenantSubscriptionService tenantSubscriptionService,
                                          TenantBillingProfileService tenantBillingProfileService) {
        this.tenantSubscriptionService = tenantSubscriptionService;
        this.tenantBillingProfileService = tenantBillingProfileService;
    }

    @Transactional
    public void provisionDefaultFreePlan(UUID tenantId) {
        TenantSubscription subscription = tenantSubscriptionService.ensureFreeSubscriptionForTenant(tenantId);
        tenantBillingProfileService.ensureProfileForPlan(
                tenantId,
                subscription.getBillingPlan(),
                null,
                "Default FREE plan assigned during tenant signup"
        );
    }
}
