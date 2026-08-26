package com.rinoimob.service.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.rinoimob.domain.dto.BillingInvoiceResponse;
import com.rinoimob.domain.entity.TenantBillingPayment;
import com.rinoimob.domain.enums.BillingPaymentStatus;
import com.rinoimob.domain.enums.BillingProvider;
import com.rinoimob.domain.repository.TenantBillingPaymentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
public class TenantBillingPaymentService {

    private static final List<BillingPaymentStatus> OPEN_STATUSES = List.of(
            BillingPaymentStatus.PENDING,
            BillingPaymentStatus.RISK_ANALYSIS,
            BillingPaymentStatus.AUTHORIZED,
            BillingPaymentStatus.OVERDUE
    );

    private final TenantBillingPaymentRepository repository;

    public TenantBillingPaymentService(TenantBillingPaymentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TenantBillingPayment upsert(UUID tenantId, String event, JsonNode payment, LocalDateTime eventAt) {
        String paymentId = text(payment, "id");
        if (paymentId == null || paymentId.isBlank()) {
            return null;
        }
        TenantBillingPayment billingPayment = repository.findByProviderPaymentId(paymentId)
                .orElseGet(TenantBillingPayment::new);
        if (billingPayment.getTenantId() != null && !billingPayment.getTenantId().equals(tenantId)) {
            throw new IllegalStateException("Asaas payment is already linked to another tenant");
        }
        if (billingPayment.getLastProviderEventAt() != null && eventAt != null
                && eventAt.isBefore(billingPayment.getLastProviderEventAt())) {
            return billingPayment;
        }
        billingPayment.setTenantId(tenantId);
        billingPayment.setProvider(BillingProvider.ASAAS);
        billingPayment.setProviderPaymentId(paymentId);
        billingPayment.setProviderSubscriptionId(firstNonBlank(text(payment, "subscription"), billingPayment.getProviderSubscriptionId()));
        billingPayment.setProviderCheckoutId(firstNonBlank(text(payment, "checkoutSession"), billingPayment.getProviderCheckoutId()));
        billingPayment.setProviderCustomerId(firstNonBlank(text(payment, "customer"), billingPayment.getProviderCustomerId()));
        billingPayment.setExternalReference(firstNonBlank(text(payment, "externalReference"), billingPayment.getExternalReference()));
        billingPayment.setStatus(resolveStatus(event, billingPayment.getStatus()));
        billingPayment.setBillingType(firstNonBlank(text(payment, "billingType"), billingPayment.getBillingType()));
        billingPayment.setValue(decimal(payment, "value", billingPayment.getValue()));
        billingPayment.setNetValue(decimal(payment, "netValue", billingPayment.getNetValue()));
        billingPayment.setDueDate(date(payment, "dueDate", billingPayment.getDueDate()));
        billingPayment.setConfirmedAt(dateTime(payment, "confirmedDate", billingPayment.getConfirmedAt()));
        billingPayment.setReceivedAt(dateTime(payment, "paymentDate", billingPayment.getReceivedAt()));
        billingPayment.setInvoiceUrl(firstNonBlank(text(payment, "invoiceUrl"), billingPayment.getInvoiceUrl()));
        billingPayment.setReceiptUrl(firstNonBlank(text(payment, "transactionReceiptUrl"), billingPayment.getReceiptUrl()));
        billingPayment.setDescription(firstNonBlank(text(payment, "description"), billingPayment.getDescription()));
        billingPayment.setLastProviderEventAt(eventAt == null ? LocalDateTime.now() : eventAt);
        return repository.save(billingPayment);
    }

    @Transactional(readOnly = true)
    public Page<BillingInvoiceResponse> list(UUID tenantId, Collection<BillingPaymentStatus> statuses,
                                              int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "dueDate", "createdAt"));
        Page<TenantBillingPayment> payments = statuses == null || statuses.isEmpty()
                ? repository.findAllByTenantId(tenantId, pageable)
                : repository.findAllByTenantIdAndStatusIn(tenantId, statuses, pageable);
        return payments.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public BillingInvoiceResponse findNextCharge(UUID tenantId) {
        return repository.findFirstByTenantIdAndStatusInOrderByDueDateAsc(tenantId, OPEN_STATUSES)
                .map(this::toResponse)
                .orElse(null);
    }

    private BillingInvoiceResponse toResponse(TenantBillingPayment payment) {
        return new BillingInvoiceResponse(
                payment.getProviderPaymentId(), payment.getStatus(), payment.getBillingType(), payment.getValue(),
                payment.getNetValue(), payment.getDueDate(), payment.getConfirmedAt(), payment.getReceivedAt(),
                payment.getInvoiceUrl(), payment.getReceiptUrl(), payment.getDescription()
        );
    }

    private BillingPaymentStatus resolveStatus(String event, BillingPaymentStatus current) {
        if (current == BillingPaymentStatus.REFUNDED || current == BillingPaymentStatus.CHARGEBACK
                || current == BillingPaymentStatus.DELETED) {
            return current;
        }
        return switch (event) {
            case "PAYMENT_CREATED", "PAYMENT_UPDATED" -> current == null ? BillingPaymentStatus.PENDING : current;
            case "PAYMENT_AWAITING_RISK_ANALYSIS", "PAYMENT_REPROVED_BY_RISK_ANALYSIS" -> BillingPaymentStatus.RISK_ANALYSIS;
            case "PAYMENT_AUTHORIZED", "PAYMENT_APPROVED_BY_RISK_ANALYSIS" -> BillingPaymentStatus.AUTHORIZED;
            case "PAYMENT_CONFIRMED" -> BillingPaymentStatus.CONFIRMED;
            case "PAYMENT_RECEIVED" -> BillingPaymentStatus.RECEIVED;
            case "PAYMENT_OVERDUE" -> BillingPaymentStatus.OVERDUE;
            case "PAYMENT_REFUNDED", "PAYMENT_PARTIALLY_REFUNDED" -> BillingPaymentStatus.REFUNDED;
            case "PAYMENT_CHARGEBACK_REQUESTED", "PAYMENT_CHARGEBACK_DISPUTE", "PAYMENT_AWAITING_CHARGEBACK_REVERSAL" -> BillingPaymentStatus.CHARGEBACK;
            case "PAYMENT_DELETED" -> BillingPaymentStatus.DELETED;
            default -> current == null ? BillingPaymentStatus.UNKNOWN : current;
        };
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    private BigDecimal decimal(JsonNode node, String field, BigDecimal fallback) {
        JsonNode value = node.get(field);
        return value == null || !value.isNumber() ? fallback : value.decimalValue();
    }

    private LocalDate date(JsonNode node, String field, LocalDate fallback) {
        String value = text(node, field);
        try {
            return value == null || value.isBlank() ? fallback : LocalDate.parse(value.substring(0, 10));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private LocalDateTime dateTime(JsonNode node, String field, LocalDateTime fallback) {
        LocalDate value = date(node, field, null);
        return value == null ? fallback : value.atStartOfDay();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
