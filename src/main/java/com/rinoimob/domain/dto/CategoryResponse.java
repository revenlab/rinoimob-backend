package com.rinoimob.domain.dto;

import java.util.UUID;

public record CategoryResponse(
    UUID id,
    UUID tenantId,
    String name,
    String slug,
    String color,
    Boolean active,
    Integer position,
    boolean isGlobal
) {}
