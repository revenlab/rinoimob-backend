package com.rinoimob.service.billing.payment.dto;

public record BillingCustomerRequest(
        String name,
        String email,
        String cpfCnpj,
        String phone,
        String address,
        String addressNumber,
        String addressComplement,
        String postalCode,
        String province,
        String externalReference
) {
}
