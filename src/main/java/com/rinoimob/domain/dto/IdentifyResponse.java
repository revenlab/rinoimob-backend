package com.rinoimob.domain.dto;

import java.util.List;

public record IdentifyResponse(
        String preAuthToken,
        List<TenantSummary> tenants
) {
}
