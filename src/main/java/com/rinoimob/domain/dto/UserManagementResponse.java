package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.VerificationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserManagementResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String phone,
    boolean active,
    String systemRole,
    UUID tenantRoleId,
    String tenantRoleName,
    LocalDateTime createdAt,
    VerificationStatus verificationStatus
) {}
