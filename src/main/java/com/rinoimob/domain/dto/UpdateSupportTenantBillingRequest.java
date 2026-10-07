package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.BillingPlanCode;

public record UpdateSupportTenantBillingRequest(
        BillingPlanCode planCode,
        Integer maxUsers,
        Integer maxProperties,
        Integer maxLeadsPerMonth,
        Integer maxWhatsappNumbers,
        Boolean blogEnabled,
        Boolean customDomainEnabled,
        Boolean automationCrmEnabled,
        Boolean publicApiEnabled,
        Boolean integrationsEnabled,
        Boolean vipSupportEnabled,
        Boolean customImplementationEnabled,
        String notes
) {
}
