package com.rinoimob.domain.dto;

import java.util.UUID;

public record TenantSummary(
        UUID id,
        String name,
        String subdomain
) {
}
