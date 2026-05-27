package com.rinoimob.domain.dto.blog;

import java.time.LocalDateTime;
import java.util.UUID;

public record PublicBlogPostResponse(
        UUID id,
        String title,
        String slug,
        String excerpt,
        String contentHtml,
        String coverImageUrl,
        LocalDateTime publishedAt
) {}
