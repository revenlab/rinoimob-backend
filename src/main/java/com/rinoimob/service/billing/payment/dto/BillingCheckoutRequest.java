package com.rinoimob.service.billing.payment.dto;

import com.rinoimob.domain.enums.BillingPlanCode;

import java.util.UUID;

public record BillingCheckoutRequest(
        UUID tenantId,
        BillingPlanCode planCode,
        long amountInCents,
        String customerName,
        String customerEmail,
        String customerId,
        String successUrl,
        String cancelUrl
) {
}
