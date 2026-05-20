package com.rinoimob.domain.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record SupportTenantSummaryResponse(
        UUID id,
        String name,
        String subdomain,
        Boolean active,
        LocalDateTime createdAt,
        long userCount
) {
}
