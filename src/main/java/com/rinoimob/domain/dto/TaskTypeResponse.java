package com.rinoimob.domain.dto;

import java.util.UUID;

public record TaskTypeResponse(
        UUID id,
        String name,
        String color,
        String icon,
        int position,
        boolean system
) {}
