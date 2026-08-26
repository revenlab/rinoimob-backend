package com.rinoimob.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBillingCustomerDetailsRequest(
        @NotBlank(message = "CPF or CNPJ is required")
        @Size(max = 18, message = "CPF or CNPJ is too long")
        String cpfCnpj,
        @NotBlank(message = "Phone is required")
        @Size(max = 20, message = "Phone is too long")
        String phone,
        @NotBlank(message = "Address is required")
        @Size(max = 255, message = "Address is too long")
        String address,
        @NotBlank(message = "Address number is required")
        @Size(max = 30, message = "Address number is too long")
        String addressNumber,
        @Size(max = 255, message = "Address complement is too long")
        String addressComplement,
        @NotBlank(message = "Postal code is required")
        @Size(max = 10, message = "Postal code is too long")
        String postalCode,
        @NotBlank(message = "Neighborhood is required")
        @Size(max = 120, message = "Neighborhood is too long")
        String province
) {
}
