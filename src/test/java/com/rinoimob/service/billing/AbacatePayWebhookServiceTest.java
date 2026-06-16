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
class AbacatePayWebhookServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    private TenantSubscriptionRepository tenantSubscriptionRepository;
    @Mock
    private BillingPlanResolverService billingPlanResolverService;
    @Mock
    private TenantBillingProfileService tenantBillingProfileService;

    private AbacatePayWebhookService service;

    @BeforeEach
    void setUp() {
        service = new AbacatePayWebhookService(
                tenantSubscriptionRepository,
                billingPlanResolverService,
                tenantBillingProfileService
        );
    }

    @Test
    void handleWebhook_resolvesTenantFromCompositeExternalIdPrefix() throws Exception {
        BillingPlan plan = billingPlan(BillingPlanCode.PRIME);
        when(billingPlanResolverService.getRequiredGlobalPlan(BillingPlanCode.PRIME)).thenReturn(plan);
        when(tenantSubscriptionRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        service.handleWebhook(new ObjectMapper().readTree("""
                {
                  "event": "subscription.completed",
                  "data": {
                    "subscription": {
                      "id": "subs_123",
                      "externalId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa-PRIME-11111111-1111-1111-1111-111111111111",
                      "metadata": {
                        "planCode": "PRIME"
                      }
                    }
                  }
                }
                """));

        ArgumentCaptor<TenantSubscription> captor = ArgumentCaptor.forClass(TenantSubscription.class);
        verify(tenantSubscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(captor.getValue().getBillingPlan().getCode()).isEqualTo(BillingPlanCode.PRIME);
        assertThat(captor.getValue().getProvider()).isEqualTo(BillingProvider.ABACATEPAY);
        assertThat(captor.getValue().getStatus()).isEqualTo(BillingSubscriptionStatus.ACTIVE);
    }

    @Test
    void handleWebhook_fallsBackToMetadataTenantIdWhenExternalIdIsInvalid() throws Exception {
        BillingPlan plan = billingPlan(BillingPlanCode.STARTER);
        when(billingPlanResolverService.getRequiredGlobalPlan(BillingPlanCode.STARTER)).thenReturn(plan);
        when(tenantSubscriptionRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        service.handleWebhook(new ObjectMapper().readTree("""
                {
                  "event": "subscription.completed",
                  "data": {
                    "externalId": "bill_fDhYBrqEd0wbUZPEQHpUcnnz",
                    "metadata": {
                      "tenantId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                      "planCode": "STARTER"
                    },
                    "subscription": {
                      "id": "subs_456"
                    }
                  }
                }
                """));

        ArgumentCaptor<TenantSubscription> captor = ArgumentCaptor.forClass(TenantSubscription.class);
        verify(tenantSubscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(captor.getValue().getBillingPlan().getCode()).isEqualTo(BillingPlanCode.STARTER);
    }

    @Test
    void handleWebhook_ignoresCancellationFromDifferentSubscriptionId() throws Exception {
        BillingPlan plan = billingPlan(BillingPlanCode.STARTER);
        when(billingPlanResolverService.getRequiredGlobalPlan(BillingPlanCode.STARTER)).thenReturn(plan);

        TenantSubscription currentSubscription = new TenantSubscription();
        currentSubscription.setTenantId(TENANT_ID);
        currentSubscription.setBillingPlan(plan);
        currentSubscription.setStatus(BillingSubscriptionStatus.ACTIVE);
        currentSubscription.setProvider(BillingProvider.ABACATEPAY);
        currentSubscription.setProviderSubscriptionId("subs_active_current");
        currentSubscription.setCurrentPeriodStart(LocalDateTime.now().minusDays(5));
        currentSubscription.setLastPlanChangeAt(LocalDateTime.now().minusDays(5));
        when(tenantSubscriptionRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(currentSubscription));

        service.handleWebhook(new ObjectMapper().readTree("""
                {
                  "event": "subscription.cancelled",
                  "data": {
                    "subscription": {
                      "id": "subs_old_canceled",
                      "metadata": {
                        "tenantId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                        "planCode": "STARTER"
                      }
                    }
                  }
                }
                """));

        verify(tenantSubscriptionRepository, never()).save(any(TenantSubscription.class));
        verify(tenantBillingProfileService, never()).replaceProfileForPlan(any(UUID.class), any(BillingPlan.class), any(), anyString());
    }

    private BillingPlan billingPlan(BillingPlanCode code) {
        BillingPlan plan = new BillingPlan();
        plan.setCode(code);
        plan.setMonthlyPrice(BigDecimal.valueOf(99));
        plan.setSortOrder(code.ordinal());
        return plan;
    }
}
