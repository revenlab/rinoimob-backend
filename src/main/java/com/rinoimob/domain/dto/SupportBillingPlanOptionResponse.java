package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.BillingPlanCode;

import java.math.BigDecimal;

public record SupportBillingPlanOptionResponse(
        BillingPlanCode code,
        String planName,
        BigDecimal monthlyPrice,
        BigDecimal annualPrice,
        Integer maxUsers,
        Integer maxProperties,
        Integer maxLeadsPerMonth,
        Integer maxWhatsappNumbers,
        boolean blogEnabled,
        boolean customDomainEnabled,
        boolean automationCrmEnabled,
        boolean publicApiEnabled,
        boolean vipSupportEnabled,
        boolean customImplementationEnabled
) {
}
