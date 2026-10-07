package com.rinoimob.domain.dto.integration;

import java.util.Map;
import java.util.UUID;

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
        boolean active
) {}
