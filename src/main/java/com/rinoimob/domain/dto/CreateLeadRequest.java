package com.rinoimob.domain.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateLeadRequest(
        @NotBlank String name,
        String email,
        String phone,
        String message,
        UUID propertyId,
        String source
) {
    public CreateLeadRequest {
        if (source == null) source = "MANUAL";
    }
}
