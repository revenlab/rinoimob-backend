package com.rinoimob.domain.dto;

import java.util.List;

public record SupportDashboardResponse(
        long totalTenants,
        long activeTenants,
        long inactiveTenants,
        long totalUsers,
        List<SupportAuditLogResponse> recentActivity
) {
}
