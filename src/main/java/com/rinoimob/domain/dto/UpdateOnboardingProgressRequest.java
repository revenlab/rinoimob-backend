package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.OnboardingStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOnboardingProgressRequest(
        @NotNull OnboardingStatus status,
        String lastStepKey,
        String lastRoute
) {}
