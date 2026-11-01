package com.rinoimob.domain.dto.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record IntegrationRouteRequest(
        @NotBlank String externalResourceId,
        String externalResourceName,
        @NotNull UUID pipelineId,
        UUID poolId,
        UUID assignedTo,
        UUID defaultPropertyId,
        Map<String, Object> fieldMapping,
        Boolean active
) {}
