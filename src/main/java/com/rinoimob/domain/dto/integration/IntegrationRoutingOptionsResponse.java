package com.rinoimob.domain.dto.integration;

import java.util.List;

public record IntegrationRoutingOptionsResponse(
        List<IntegrationOptionResponse> pipelines,
        List<IntegrationOptionResponse> pools,
        List<IntegrationOptionResponse> brokers,
        List<IntegrationOptionResponse> properties
) {}
