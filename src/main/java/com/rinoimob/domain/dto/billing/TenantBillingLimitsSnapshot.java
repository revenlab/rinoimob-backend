package com.rinoimob.domain.dto.billing;

import com.rinoimob.domain.enums.BillingPlanCode;

import java.util.UUID;

public record TenantBillingLimitsSnapshot(
        UUID tenantId,
        BillingPlanCode planCode,
        String planName,
        int maxUsers,
        int maxProperties,
        int maxLeadsPerMonth,
        int maxWhatsappNumbers,
        boolean blogEnabled,
        boolean customDomainEnabled,
        boolean automationCrmEnabled,
        boolean publicApiEnabled,
        boolean vipSupportEnabled,
        boolean customImplementationEnabled
) {
    public static final int UNLIMITED = -1;

    public static int normalizeLimit(Integer value) {
        return value == null ? UNLIMITED : value;
    }
}
