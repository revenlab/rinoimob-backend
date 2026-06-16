package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.BillingPlanCode;
import com.rinoimob.domain.enums.BillingProvider;
import com.rinoimob.domain.enums.BillingSubscriptionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TenantBillingPortalResponse(
        UUID tenantId,
        BillingPlanCode currentPlanCode,
        String currentPlanName,
        BillingSubscriptionStatus subscriptionStatus,
        BillingProvider provider,
        LocalDateTime currentPeriodStart,
        LocalDateTime currentPeriodEnd,
        int maxUsers,
        int maxProperties,
        int maxLeadsPerMonth,
        int maxWhatsappNumbers,
        boolean blogEnabled,
        boolean customDomainEnabled,
        boolean automationCrmEnabled,
        boolean publicApiEnabled,
        boolean vipSupportEnabled,
        boolean customImplementationEnabled,
        List<TenantBillingPlanOptionResponse> availablePlans
) {
}
