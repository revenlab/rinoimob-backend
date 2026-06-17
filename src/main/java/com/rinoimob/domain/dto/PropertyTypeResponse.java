package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.PropertyType;

import java.util.UUID;

public record PropertyTypeResponse(
        UUID id,
        PropertyType code,
        String label,
        int position,
        boolean active,
        String cardColor,
        String coverImageUrl
) {}
