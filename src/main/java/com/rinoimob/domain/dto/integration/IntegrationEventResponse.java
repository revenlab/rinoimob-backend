package com.rinoimob.domain.dto.integration;

import com.rinoimob.domain.enums.IntegrationEventStatus;
import com.rinoimob.domain.enums.IntegrationProvider;

import java.time.LocalDateTime;
import java.util.UUID;

public record IntegrationEventResponse(
        UUID id,
        UUID connectionId,
        IntegrationProvider provider,
        String externalEventId,
        String eventType,
        IntegrationEventStatus status,
        int attemptCount,
        String lastError,
        UUID leadId,
        LocalDateTime receivedAt,
        LocalDateTime processedAt
) {}
