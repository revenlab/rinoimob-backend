package com.rinoimob.domain.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UpdateTaskRequest(
        String title,
        String description,
        UUID leadId,
        UUID assignedTo,
        LocalDateTime dueAt,
        UUID taskTypeId
) {}
