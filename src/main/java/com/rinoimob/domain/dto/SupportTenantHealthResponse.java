package com.rinoimob.domain.dto;

import java.util.List;
import java.util.UUID;

public record SupportTenantHealthResponse(
        UUID tenantId,
        String tenantName,
        String subdomain,
        Boolean active,
        String status,
        List<String> issues,
        long totalUsers,
        long activeUsers,
        long inactiveUsers,
        long pendingInvites,
        String emailSenderStatus,
        long emailSenderConfigCount,
        String defaultEmailSenderName,
        String defaultEmailSenderEmail,
        long failedExecutionsLast7Days,
        List<SupportUserSummaryResponse> pendingInviteUsers,
        List<SupportUserSummaryResponse> inactiveUsersSample,
        List<SupportTenantHealthFailureResponse> recentFailures
) {
}
