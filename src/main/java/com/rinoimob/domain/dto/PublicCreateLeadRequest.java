package com.rinoimob.domain.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record PublicCreateLeadRequest(
        @NotBlank String name,
        String email,
        String phone,
        String message,
        UUID propertyId
) {}
