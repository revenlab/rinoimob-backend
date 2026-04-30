package com.rinoimob.domain.dto;

import java.util.List;
import java.util.UUID;

public record TenantRoleResponse(
    UUID id,
    UUID tenantId,
    String name,
    String description,
    Boolean isSystem,
    List<String> permissions
) {}
