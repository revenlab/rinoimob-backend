package com.rinoimob.domain.dto;

public record StartBillingCheckoutResponse(
        String checkoutId,
        String checkoutUrl,
        String expiresAt,
        String changeStatus,
        java.time.LocalDateTime effectiveAt,
        boolean requiresCheckout
) {
}
