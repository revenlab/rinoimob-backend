package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.BillingPlanCode;
import com.rinoimob.domain.enums.BillingProvider;
import com.rinoimob.domain.enums.BillingSubscriptionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SupportTenantBillingResponse(
        UUID tenantId,
        BillingPlanCode planCode,
        String planName,
        BillingSubscriptionStatus subscriptionStatus,
        BillingProvider provider,
        String providerCustomerId,
        String providerSubscriptionId,
        String providerCheckoutId,
        LocalDateTime currentPeriodStart,
        LocalDateTime currentPeriodEnd,
        boolean cancelAtPeriodEnd,
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
        String notes,
        UUID updatedByUserId,
        LocalDateTime updatedAt,
        List<SupportBillingPlanOptionResponse> availablePlans
) {
}
