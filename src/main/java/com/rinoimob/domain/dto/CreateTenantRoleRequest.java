package com.rinoimob.domain.dto;

import java.util.List;

public record CreateTenantRoleRequest(
    String name,
    String description,
    List<String> permissions
) {}
