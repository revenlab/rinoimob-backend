package com.rinoimob.domain.dto.integration;

import com.rinoimob.domain.enums.IntegrationConnectionStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateIntegrationStatusRequest(@NotNull IntegrationConnectionStatus status) {}
