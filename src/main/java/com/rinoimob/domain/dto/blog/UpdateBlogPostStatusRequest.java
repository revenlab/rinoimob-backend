package com.rinoimob.domain.dto.blog;

import com.rinoimob.domain.enums.BlogPostStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateBlogPostStatusRequest(
        @NotNull BlogPostStatus status
) {}
