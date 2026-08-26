package com.rinoimob.service.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.domain.entity.TenantBillingPayment;
import com.rinoimob.domain.enums.BillingPaymentStatus;
import com.rinoimob.domain.repository.TenantBillingPaymentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantBillingPaymentServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void shouldCreateTenantLedgerEntryFromOverduePayment() throws Exception {
        TenantBillingPaymentRepository repository = mock(TenantBillingPaymentRepository.class);
        TenantBillingPaymentService service = new TenantBillingPaymentService(repository);
        when(repository.findByProviderPaymentId("pay_123")).thenReturn(Optional.empty());
        when(repository.save(any(TenantBillingPayment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantBillingPayment payment = service.upsert(
                TENANT_ID,
                "PAYMENT_OVERDUE",
                new ObjectMapper().readTree("""
                        {
                          "id": "pay_123",
                          "subscription": "sub_123",
                          "customer": "cus_123",
                          "billingType": "CREDIT_CARD",
                          "value": 10.00,
                          "netValue": 9.32,
                          "dueDate": "2026-08-21",
                          "invoiceUrl": "https://sandbox.asaas.com/i/pay_123"
                        }
                        """),
                LocalDateTime.of(2026, 8, 24, 23, 19, 33)
        );

        assertThat(payment.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(payment.getStatus()).isEqualTo(BillingPaymentStatus.OVERDUE);
        assertThat(payment.getProviderSubscriptionId()).isEqualTo("sub_123");
        assertThat(payment.getValue()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(payment.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 21));
        assertThat(payment.getInvoiceUrl()).endsWith("pay_123");
    }

    @Test
    void shouldIgnoreAnOlderProviderEventForTheSamePayment() throws Exception {
        TenantBillingPaymentRepository repository = mock(TenantBillingPaymentRepository.class);
        TenantBillingPaymentService service = new TenantBillingPaymentService(repository);
        TenantBillingPayment current = new TenantBillingPayment();
        current.setTenantId(TENANT_ID);
        current.setProviderPaymentId("pay_123");
        current.setStatus(BillingPaymentStatus.CONFIRMED);
        current.setLastProviderEventAt(LocalDateTime.of(2026, 8, 25, 10, 0));
        when(repository.findByProviderPaymentId("pay_123")).thenReturn(Optional.of(current));

        TenantBillingPayment result = service.upsert(
                TENANT_ID,
                "PAYMENT_OVERDUE",
                new ObjectMapper().readTree("{ \"id\": \"pay_123\" }"),
                LocalDateTime.of(2026, 8, 24, 10, 0)
        );

        assertThat(result.getStatus()).isEqualTo(BillingPaymentStatus.CONFIRMED);
        verify(repository, never()).save(any());
    }
}
