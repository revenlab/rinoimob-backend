package com.rinoimob.domain.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record LeadPoolResponse(
        UUID id,
        UUID tenantId,
        String name,
        String description,
        LocalDateTime createdAt
) {}
