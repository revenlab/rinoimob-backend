package com.rinoimob.domain.dto.property;

import com.rinoimob.domain.enums.PropertyVideoSource;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public record PropertyVideoResponse(
        UUID id,
        PropertyVideoSource source,
        String url,
        String youtubeVideoId,
        String title,
        Integer position,
        LocalDateTime createdAt
) implements Serializable {}
