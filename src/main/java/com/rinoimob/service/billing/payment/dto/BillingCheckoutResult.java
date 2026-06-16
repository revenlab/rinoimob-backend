package com.rinoimob.service.billing.payment.dto;

public record BillingCheckoutResult(
        String checkoutId,
        String checkoutUrl,
        String providerCustomerId,
        String providerSubscriptionId,
        String expiresAt
) {
}
