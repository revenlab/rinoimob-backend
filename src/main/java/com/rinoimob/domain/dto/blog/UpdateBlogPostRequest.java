package com.rinoimob.domain.dto.blog;

import com.rinoimob.domain.enums.BlogPostStatus;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record UpdateBlogPostRequest(
        @Size(max = 180) String title,
        @Size(max = 180) String slug,
        @Size(max = 400) String excerpt,
        String contentHtml,
        @Size(max = 500) String coverImageUrl,
        BlogPostStatus status,
        LocalDateTime publishedAt
) {}
