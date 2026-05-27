package com.rinoimob.domain.dto.blog;

import com.rinoimob.domain.enums.BlogPostStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record BlogPostResponse(
        UUID id,
        UUID tenantId,
        String title,
        String slug,
        String excerpt,
        String contentHtml,
        String coverImageUrl,
        BlogPostStatus status,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        UUID createdBy,
        String createdByName,
        UUID updatedBy,
        String updatedByName
) {}
