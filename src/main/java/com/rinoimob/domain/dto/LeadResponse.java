package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.LeadStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record LeadResponse(
        UUID id,
        UUID tenantId,
        UUID propertyId,
        String name,
        String email,
        String phone,
        String message,
        LeadStatus status,
        String source,
        UUID assignedTo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<LeadNoteResponse> notes
) {}
