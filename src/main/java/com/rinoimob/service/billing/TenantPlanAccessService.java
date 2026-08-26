package com.rinoimob.service.billing;

import com.rinoimob.domain.dto.billing.TenantBillingLimitsSnapshot;
import com.rinoimob.domain.enums.BillingFeature;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class TenantPlanAccessService {

    private final TenantBillingProfileService tenantBillingProfileService;

    public TenantPlanAccessService(TenantBillingProfileService tenantBillingProfileService) {
        this.tenantBillingProfileService = tenantBillingProfileService;
    }

    public boolean isEnabled(UUID tenantId, BillingFeature feature) {
        TenantBillingLimitsSnapshot limits = tenantBillingProfileService.resolveEffectiveLimits(tenantId);
        return switch (feature) {
            case BLOG -> limits.blogEnabled();
            case CUSTOM_DOMAIN -> limits.customDomainEnabled();
            case AUTOMATION_CRM -> limits.automationCrmEnabled();
            case PUBLIC_API -> limits.publicApiEnabled();
        };
    }

    public void requireEnabled(UUID tenantId, BillingFeature feature) {
        if (isEnabled(tenantId, feature)) {
            return;
        }

        throw new ResponseStatusException(
                HttpStatus.PAYMENT_REQUIRED,
                feature.getDisplayName() + " está disponível a partir do plano "
                        + formatPlanName(feature.getRequiredPlanCode().name())
        );
    }

    private String formatPlanName(String planCode) {
        return planCode.charAt(0) + planCode.substring(1).toLowerCase();
    }
}
