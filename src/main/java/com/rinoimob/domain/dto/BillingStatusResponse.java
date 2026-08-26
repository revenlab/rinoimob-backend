package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.BillingPlanCode;
import com.rinoimob.domain.enums.BillingSubscriptionStatus;

import java.time.LocalDateTime;

public record BillingStatusResponse(
        BillingPlanCode currentPlanCode,
        BillingSubscriptionStatus subscriptionStatus,
        LocalDateTime paymentDueDate,
        LocalDateTime accessRestrictedAt,
        boolean cancelAtPeriodEnd,
        boolean blogEnabled,
        boolean customDomainEnabled,
        boolean automationCrmEnabled,
        boolean publicApiEnabled,
        boolean vipSupportEnabled,
        boolean customImplementationEnabled
) {
}
