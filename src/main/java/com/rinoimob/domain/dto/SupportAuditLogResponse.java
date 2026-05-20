package com.rinoimob.domain.dto;

import java.time.LocalDateTime;

public record SupportAuditLogResponse(
        Long id,
        String tenantId,
        String tenantName,
        String userId,
        String userName,
        String action,
        String resource,
        String resourceId,
        String targetLabel,
        String details,
        LocalDateTime createdAt
) {
}
