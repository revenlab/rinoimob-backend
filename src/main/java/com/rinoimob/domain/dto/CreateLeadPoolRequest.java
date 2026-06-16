package com.rinoimob.domain.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record CreateLeadPoolRequest(
        @NotBlank String name,
        String description,
        String criteria,
        Integer priority,
        String routingStrategy,
        String brokerSelectionMode,
        List<UUID> brokerIds,
        Integer triggerAfterInactiveDays
) {}
