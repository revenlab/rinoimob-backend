package com.rinoimob.domain.dto;

import java.util.List;
import java.util.UUID;

public record UpdateLeadPoolRequest(
        String name,
        String description,
        String criteria,
        Integer priority,
        String routingStrategy,
        String brokerSelectionMode,
        List<UUID> brokerIds,
        Integer triggerAfterInactiveDays
) {}
