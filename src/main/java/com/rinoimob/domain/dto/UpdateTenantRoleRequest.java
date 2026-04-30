package com.rinoimob.domain.dto;

import java.util.List;

public record UpdateTenantRoleRequest(
    String name,
    String description,
    List<String> permissions
) {}
