package com.rinoimob.domain.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID tenantId,
        UUID leadId,
        String leadName,
        UUID assignedTo,
        String assignedToName,
        String title,
        String description,
        LocalDateTime dueAt,
        boolean completed,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean overdue
) {}
