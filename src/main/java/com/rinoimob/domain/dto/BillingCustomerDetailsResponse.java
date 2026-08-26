package com.rinoimob.domain.dto;

public record BillingCustomerDetailsResponse(
        String cpfCnpj,
        String phone,
        String address,
        String addressNumber,
        String addressComplement,
        String postalCode,
        String province,
        boolean complete
) {
}
