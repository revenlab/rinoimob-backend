package com.rinoimob.service.billing.payment.dto;

public record BillingCustomerResult(
        String customerId,
        String email,
        String name
) {
}
