package com.rinoimob.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateLeadPoolRequest(
        @NotBlank String name,
        String description
) {}
