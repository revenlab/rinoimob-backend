package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.VerificationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

import com.rinoimob.domain.enums.SystemRole;

public record SupportUserSummaryResponse(
        UUID id,
        UUID tenantId,
        String email,
        String firstName,
        String lastName,
        Boolean active,
        SystemRole systemRole,
        UUID tenantRoleId,
        String tenantRoleName,
        VerificationStatus verificationStatus,
        LocalDateTime createdAt
) {
}
