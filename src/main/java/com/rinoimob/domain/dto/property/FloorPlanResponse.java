package com.rinoimob.domain.dto.property;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record FloorPlanResponse(
        UUID id,
        String name,
        BigDecimal area,
        LocalDateTime createdAt,
        List<FloorPlanPhotoResponse> photos
) {}
