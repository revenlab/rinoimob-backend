package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.SystemRole;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

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
    String tenantSubdomain,
    Set<String> supportPermissions,
    OnboardingSummaryResponse onboarding
) {}
