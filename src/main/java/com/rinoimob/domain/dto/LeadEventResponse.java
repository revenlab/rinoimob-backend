package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.LeadEventType;

import java.time.LocalDateTime;
import java.util.UUID;

public record LeadEventResponse(
        UUID id,
        UUID leadId,
        UUID userId,
        LeadEventType eventType,
        String description,
        LocalDateTime createdAt
) {}
