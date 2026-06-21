package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.OnboardingStatus;

import java.time.LocalDateTime;

public record OnboardingSummaryResponse(
        String tutorialKey,
        OnboardingStatus status,
        String lastStepKey,
        String lastRoute,
        LocalDateTime completedAt,
        LocalDateTime dismissedAt
) {}
