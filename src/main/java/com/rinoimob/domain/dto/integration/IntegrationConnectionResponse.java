package com.rinoimob.domain.dto.integration;

import com.rinoimob.domain.enums.IntegrationConnectionStatus;
import com.rinoimob.domain.enums.IntegrationProvider;

import java.time.LocalDateTime;
import java.util.UUID;

public record IntegrationConnectionResponse(
        UUID id,
        IntegrationProvider provider,
        String name,
        IntegrationConnectionStatus status,
        String externalAccountId,
        String externalAccountName,
        String inboundUrl,
        String feedUrl,
        LocalDateTime lastReceivedAt,
        LocalDateTime lastProcessedAt,
        String lastError,
        long pendingEvents
) {}
