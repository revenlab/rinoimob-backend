package com.rinoimob.service.billing.payment.dto;

import java.util.List;
import java.util.Map;

public record BillingProviderPage(
        List<Map<String, Object>> data,
        boolean hasMore
) {
}
