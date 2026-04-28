package com.rinoimob.domain.dto.property;

import java.time.LocalDateTime;
import java.util.UUID;

public record FloorPlanPhotoResponse(
        UUID id,
        String url,
        Integer position,
        LocalDateTime createdAt
) {}
