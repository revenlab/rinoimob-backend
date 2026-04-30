package com.rinoimob.domain.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserManagementResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    boolean active,
    String systemRole,
    UUID tenantRoleId,
    String tenantRoleName,
    LocalDateTime createdAt
) {}
