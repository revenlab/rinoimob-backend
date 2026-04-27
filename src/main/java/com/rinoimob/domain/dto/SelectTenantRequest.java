package com.rinoimob.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SelectTenantRequest(
        @NotBlank(message = "Pre-auth token is required")
        String preAuthToken,

        @NotNull(message = "Tenant ID is required")
        UUID tenantId
) {
}
