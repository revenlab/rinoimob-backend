package com.rinoimob.domain.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.rinoimob.domain.enums.SystemRole;

public record MeResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    Boolean active,
    SystemRole systemRole,
    LocalDateTime createdAt,
    UUID tenantId,
    String tenantName,
    String tenantSubdomain
) {}
