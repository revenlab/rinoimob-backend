package com.rinoimob.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateSupportTenantRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Subdomain is required")
        String subdomain
) {
}
