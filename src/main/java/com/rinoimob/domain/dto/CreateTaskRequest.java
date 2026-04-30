package com.rinoimob.domain.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateTaskRequest(
        @NotBlank String title,
        String description,
        UUID leadId,
        UUID assignedTo,
        LocalDateTime dueAt
) {}
