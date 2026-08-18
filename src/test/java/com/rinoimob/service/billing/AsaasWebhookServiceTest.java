package com.rinoimob.service.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.domain.entity.BillingPlan;
import com.rinoimob.domain.entity.TenantSubscription;
import com.rinoimob.domain.enums.BillingPlanCode;
import com.rinoimob.domain.enums.BillingProvider;
import com.rinoimob.domain.enums.BillingSubscriptionStatus;
import com.rinoimob.domain.repository.TenantSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsaasWebhookServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock private TenantSubscriptionRepository tenantSubscriptionRepository;
    @Mock private BillingPlanResolverService billingPlanResolverService;
    @Mock private TenantBillingProfileService tenantBillingProfileService;

    private AsaasWebhookService service;

    @BeforeEach
    void setUp() {
        service = new AsaasWebhookService(tenantSubscriptionRepository, billingPlanResolverService, tenantBillingProfileService);
    }

    @Test
    void handleWebhook_activatesSubscriptionFromCheckoutPayment() throws Exception {
        BillingPlan plan = billingPlan(BillingPlanCode.PRIME);
        when(billingPlanResolverService.getRequiredGlobalPlan(BillingPlanCode.PRIME)).thenReturn(plan);
        when(tenantSubscriptionRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        service.handleWebhook(new ObjectMapper().readTree("""
                { "event": "PAYMENT_RECEIVED", "payment": {
                  "customer": "cus_123", "subscription": "sub_123", "dateCreated": "2026-08-17",
                  "dueDate": "2026-08-17", "externalReference": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa-PRIME-11111111-1111-1111-1111-111111111111"
                }}
                """));

        ArgumentCaptor<TenantSubscription> captor = ArgumentCaptor.forClass(TenantSubscription.class);
        verify(tenantSubscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(captor.getValue().getBillingPlan()).isEqualTo(plan);
        assertThat(captor.getValue().getProvider()).isEqualTo(BillingProvider.ASAAS);
        assertThat(captor.getValue().getProviderSubscriptionId()).isEqualTo("sub_123");
        assertThat(captor.getValue().getStatus()).isEqualTo(BillingSubscriptionStatus.ACTIVE);
    }

    @Test
    void handleWebhook_marksOverduePaymentAsPastDue() throws Exception {
        BillingPlan plan = billingPlan(BillingPlanCode.STARTER);
        TenantSubscription subscription = subscription(plan);
        when(billingPlanResolverService.getRequiredGlobalPlan(BillingPlanCode.STARTER)).thenReturn(plan);
        when(tenantSubscriptionRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(subscription));

        service.handleWebhook(new ObjectMapper().readTree("""
                { "event": "PAYMENT_OVERDUE", "payment": {
                  "dueDate": "2026-08-17",
                  "externalReference": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa-STARTER-11111111-1111-1111-1111-111111111111"
                }}
                """));

        assertThat(subscription.getStatus()).isEqualTo(BillingSubscriptionStatus.PAST_DUE);
        assertThat(subscription.getPastDueAt()).isEqualTo(LocalDateTime.of(2026, 8, 17, 0, 0));
        verify(tenantBillingProfileService).replaceProfileForPlan(any(UUID.class), any(BillingPlan.class), any(), anyString());
    }

    @Test
    void handleWebhook_ignoresUnrelatedEvents() throws Exception {
        service.handleWebhook(new ObjectMapper().readTree("{ \"event\": \"TRANSFER_RECEIVED\" }"));
        verify(tenantSubscriptionRepository, never()).save(any());
    }

    private TenantSubscription subscription(BillingPlan plan) {
        TenantSubscription subscription = new TenantSubscription();
        subscription.setTenantId(TENANT_ID);
        subscription.setBillingPlan(plan);
        subscription.setProvider(BillingProvider.ASAAS);
        subscription.setStatus(BillingSubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(LocalDateTime.now().minusDays(1));
        return subscription;
    }

    private BillingPlan billingPlan(BillingPlanCode code) {
        BillingPlan plan = new BillingPlan();
        plan.setCode(code);
        plan.setMonthlyPrice(BigDecimal.valueOf(99));
        plan.setSortOrder(code.ordinal());
        return plan;
    }
}
