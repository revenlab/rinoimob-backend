package com.rinoimob.domain.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record LeadNoteResponse(
        UUID id,
        UUID leadId,
        UUID userId,
        String content,
        LocalDateTime createdAt
) {}
