package com.rinoimob.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateBillingCardTokenRequest(
        @NotBlank String creditCardToken
) {
}
