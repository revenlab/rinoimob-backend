package com.rinoimob.domain.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record SupportTenantHealthFailureResponse(
        UUID executionId,
        UUID workflowId,
        String workflowName,
        String triggerEvent,
        String errorMessage,
        LocalDateTime createdAt
) {
}
