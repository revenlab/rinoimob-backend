package com.rinoimob.domain.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record LeadPoolResponse(
        UUID id,
        UUID tenantId,
        String name,
        String description,
        String criteria,
        Integer priority,
        String routingStrategy,
        String brokerSelectionMode,
        Integer triggerAfterInactiveDays,
        List<UUID> brokerIds,
        LocalDateTime createdAt
) {}
