package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.BillingPaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BillingInvoiceResponse(
        String paymentId,
        BillingPaymentStatus status,
        String billingType,
        BigDecimal value,
        BigDecimal netValue,
        LocalDate dueDate,
        LocalDateTime confirmedAt,
        LocalDateTime receivedAt,
        String invoiceUrl,
        String receiptUrl,
        String description
) {
}
