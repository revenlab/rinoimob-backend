package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.BillingPlanCode;
import jakarta.validation.constraints.NotNull;

public record StartBillingCheckoutRequest(
        @NotNull(message = "Plan code is required")
        BillingPlanCode planCode
) {
}
