package com.rinoimob.domain.dto.property;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public record FloorPlanPhotoResponse(
        UUID id,
        String url,
        Integer position,
        Boolean isCover,
        LocalDateTime createdAt
) implements Serializable {}
