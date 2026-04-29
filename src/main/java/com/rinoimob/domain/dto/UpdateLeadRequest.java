package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.LeadStatus;

import java.util.UUID;

public record UpdateLeadRequest(
        String name,
        String email,
        String phone,
        String message,
        LeadStatus status,
        UUID assignedTo
) {}
