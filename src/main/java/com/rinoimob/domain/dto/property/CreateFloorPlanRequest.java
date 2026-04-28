package com.rinoimob.domain.dto.property;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CreateFloorPlanRequest(
        @NotBlank String name,
        BigDecimal area
) {}
