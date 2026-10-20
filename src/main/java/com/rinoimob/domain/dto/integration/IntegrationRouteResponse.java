package com.rinoimob.domain.dto.integration;

import java.util.Map;
import java.util.UUID;
import java.time.Instant;

public record IntegrationRouteResponse(
        UUID id,
        UUID connectionId,
        String externalResourceId,
        String externalResourceName,
        UUID pipelineId,
        UUID poolId,
        UUID assignedTo,
        UUID defaultPropertyId,
        Map<String, Object> fieldMapping,
        boolean active,
        Instant lastSyncAt,
        Instant syncedUntil,
        Instant nextSyncAt,
        String syncError,
        long recoveredLeads,
        boolean syncInProgress
) {}
