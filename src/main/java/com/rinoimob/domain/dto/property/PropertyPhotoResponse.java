package com.rinoimob.domain.dto.property;

import java.time.LocalDateTime;
import java.util.UUID;

public record PropertyPhotoResponse(
        UUID id,
        String url,
        Integer position,
        Boolean isCover,
        String altText,
        LocalDateTime createdAt
) {}
