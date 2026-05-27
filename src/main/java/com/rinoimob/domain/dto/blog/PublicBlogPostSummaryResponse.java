package com.rinoimob.domain.dto.blog;

import java.time.LocalDateTime;
import java.util.UUID;

public record PublicBlogPostSummaryResponse(
        UUID id,
        String title,
        String slug,
        String excerpt,
        String coverImageUrl,
        LocalDateTime publishedAt
) {}
