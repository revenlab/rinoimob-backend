package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.BillingPlanCode;
import com.rinoimob.domain.enums.BillingSubscriptionChangeStatus;

import java.time.LocalDateTime;

public record PendingBillingChangeResponse(
        BillingPlanCode targetPlanCode,
        String targetPlanName,
        BillingSubscriptionChangeStatus status,
        String checkoutUrl,
        LocalDateTime effectiveAt,
        LocalDateTime expiresAt
) {
}
