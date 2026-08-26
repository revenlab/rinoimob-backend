package com.rinoimob.service.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.domain.entity.BillingPlan;
import com.rinoimob.domain.entity.TenantBillingPayment;
import com.rinoimob.domain.entity.TenantBillingProfile;
import com.rinoimob.domain.entity.TenantSubscription;
import com.rinoimob.domain.entity.TenantSubscriptionChange;
import com.rinoimob.domain.enums.BillingPlanCode;
import com.rinoimob.domain.enums.BillingProvider;
import com.rinoimob.domain.enums.BillingProviderOperationType;
import com.rinoimob.domain.enums.BillingSubscriptionChangeStatus;
import com.rinoimob.domain.enums.BillingSubscriptionStatus;
import com.rinoimob.domain.repository.TenantBillingProfileRepository;
import com.rinoimob.domain.repository.TenantSubscriptionChangeRepository;
import com.rinoimob.domain.repository.TenantSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsaasWebhookServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String EXTERNAL_REFERENCE = TENANT_ID + "-PRIME-11111111-1111-1111-1111-111111111111";

    @Mock private TenantSubscriptionRepository subscriptionRepository;
    @Mock private TenantSubscriptionChangeRepository changeRepository;
    @Mock private TenantBillingProfileRepository profileRepository;
    @Mock private BillingPlanResolverService planResolverService;
    @Mock private TenantBillingProfileService profileService;
    @Mock private TenantBillingPaymentService paymentService;
    @Mock private BillingProviderOperationService operationService;

    private AsaasWebhookService service;

    @BeforeEach
    void setUp() {
        service = new AsaasWebhookService(
                subscriptionRepository, changeRepository, profileRepository, planResolverService,
                profileService, paymentService, operationService
        );
    }

    @Test
    void shouldActivatePaidUpgradeOnceAndCancelPreviousSubscriptionThroughOutbox() throws Exception {
        BillingPlan currentPlan = billingPlan(BillingPlanCode.STARTER);
        BillingPlan targetPlan = billingPlan(BillingPlanCode.PRIME);
        TenantSubscription subscription = subscription(currentPlan);
        subscription.setProviderSubscriptionId("sub_old");
        TenantSubscriptionChange change = change(targetPlan);
        change.setId(UUID.randomUUID());
        change.setPreviousProviderSubscriptionId("sub_old");

        when(changeRepository.findByExternalReference(EXTERNAL_REFERENCE)).thenReturn(Optional.of(change));
        when(subscriptionRepository.findByTenantIdForUpdate(TENANT_ID)).thenReturn(Optional.of(subscription));
        when(paymentService.upsert(eq(TENANT_ID), eq("PAYMENT_CONFIRMED"), any(), any()))
                .thenReturn(payment("pay_1", LocalDate.of(2026, 8, 25)));

        service.handleWebhook(new ObjectMapper().readTree("""
                { "event": "PAYMENT_CONFIRMED", "dateCreated": "2026-08-25 10:00:00", "payment": {
                  "id": "pay_1", "customer": "cus_123", "subscription": "sub_new",
                  "dueDate": "2026-08-25", "externalReference": "%s"
                }}
                """.formatted(EXTERNAL_REFERENCE)));

        assertThat(subscription.getBillingPlan()).isEqualTo(targetPlan);
        assertThat(subscription.getStatus()).isEqualTo(BillingSubscriptionStatus.ACTIVE);
        assertThat(subscription.getProviderSubscriptionId()).isEqualTo("sub_new");
        assertThat(change.getStatus()).isEqualTo(BillingSubscriptionChangeStatus.APPLIED);
        verify(operationService).enqueue(
                eq(TENANT_ID), eq(BillingProviderOperationType.CANCEL_SUBSCRIPTION), eq("sub_old"),
                eq("change:" + change.getId() + ":cancel-previous"), anyMap()
        );
    }

    @Test
    void shouldNotActivatePlanOnCheckoutPaidWithoutConfirmedPayment() throws Exception {
        BillingPlan currentPlan = billingPlan(BillingPlanCode.STARTER);
        TenantSubscription subscription = subscription(currentPlan);
        TenantSubscriptionChange change = change(billingPlan(BillingPlanCode.PRIME));

        when(changeRepository.findByProviderCheckoutId("checkout_123")).thenReturn(Optional.of(change));
        when(subscriptionRepository.findByTenantIdForUpdate(TENANT_ID)).thenReturn(Optional.of(subscription));

        service.handleWebhook(new ObjectMapper().readTree("""
                { "event": "CHECKOUT_PAID", "checkout": { "id": "checkout_123", "customer": "cus_123" } }
                """));

        assertThat(subscription.getBillingPlan()).isEqualTo(currentPlan);
        assertThat(subscription.getStatus()).isEqualTo(BillingSubscriptionStatus.ACTIVE);
        assertThat(change.getStatus()).isEqualTo(BillingSubscriptionChangeStatus.PAID);
        verify(operationService, never()).enqueue(any(), any(), any(), any(), anyMap());
    }

    @Test
    void shouldMarkCurrentInvoiceOverdueUsingCustomerCorrelation() throws Exception {
        BillingPlan plan = billingPlan(BillingPlanCode.STARTER);
        TenantSubscription subscription = subscription(plan);
        TenantBillingProfile profile = new TenantBillingProfile();
        profile.setTenantId(TENANT_ID);
        TenantBillingPayment payment = payment("pay_overdue", LocalDate.of(2026, 8, 21));

        when(profileRepository.findByProviderCustomerId("cus_123")).thenReturn(Optional.of(profile));
        when(subscriptionRepository.findByTenantIdForUpdate(TENANT_ID)).thenReturn(Optional.of(subscription));
        when(paymentService.upsert(eq(TENANT_ID), eq("PAYMENT_OVERDUE"), any(), any())).thenReturn(payment);

        service.handleWebhook(new ObjectMapper().readTree("""
                { "event": "PAYMENT_OVERDUE", "payment": {
                  "id": "pay_overdue", "customer": "cus_123", "dueDate": "2026-08-21",
                  "invoiceUrl": "https://sandbox.asaas.com/i/pay_overdue"
                }}
                """));

        assertThat(subscription.getStatus()).isEqualTo(BillingSubscriptionStatus.PAST_DUE);
        assertThat(subscription.getPastDueAt()).isEqualTo(LocalDateTime.of(2026, 8, 21, 0, 0));
        assertThat(subscription.getProviderInvoiceUrl()).isEqualTo("https://sandbox.asaas.com/i/pay_overdue");
        verify(profileService, never()).replaceProfileForPlan(any(), any(), any(), any());
    }

    @Test
    void shouldRestoreSuspendedPlanAfterLatePayment() throws Exception {
        BillingPlan plan = billingPlan(BillingPlanCode.PRIME);
        TenantSubscription subscription = subscription(plan);
        subscription.setStatus(BillingSubscriptionStatus.SUSPENDED);
        subscription.setProviderSubscriptionId("sub_123");
        subscription.setAccessRestrictedAt(LocalDateTime.now().minusDays(2));

        when(subscriptionRepository.findByProviderSubscriptionId("sub_123")).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.findByTenantIdForUpdate(TENANT_ID)).thenReturn(Optional.of(subscription));
        when(paymentService.upsert(eq(TENANT_ID), eq("PAYMENT_RECEIVED"), any(), any()))
                .thenReturn(payment("pay_late", LocalDate.now().minusDays(10)));

        service.handleWebhook(new ObjectMapper().readTree("""
                { "event": "PAYMENT_RECEIVED", "payment": {
                  "id": "pay_late", "subscription": "sub_123", "dueDate": "%s"
                }}
                """.formatted(LocalDate.now().minusDays(10))));

        assertThat(subscription.getStatus()).isEqualTo(BillingSubscriptionStatus.ACTIVE);
        assertThat(subscription.getAccessRestrictedAt()).isNull();
        verify(profileService).replaceProfileForPlan(TENANT_ID, plan, null, "Asaas payment restored paid access");
        verify(operationService).enqueue(
                eq(TENANT_ID), eq(BillingProviderOperationType.REACTIVATE_SUBSCRIPTION), eq("sub_123"),
                eq("payment:pay_late:reactivate"), anyMap()
        );
    }

    @Test
    void shouldIgnoreUnrelatedEvents() throws Exception {
        assertThat(service.handleWebhook(new ObjectMapper().readTree("{ \"event\": \"TRANSFER_RECEIVED\" }"))).isNull();
        verify(subscriptionRepository, never()).save(any());
    }

    private TenantSubscriptionChange change(BillingPlan targetPlan) {
        TenantSubscriptionChange change = new TenantSubscriptionChange();
        change.setTenantId(TENANT_ID);
        change.setSourcePlan(billingPlan(BillingPlanCode.STARTER));
        change.setTargetPlan(targetPlan);
        change.setStatus(BillingSubscriptionChangeStatus.PENDING);
        change.setExternalReference(EXTERNAL_REFERENCE);
        change.setProviderCheckoutId("checkout_123");
        return change;
    }

    private TenantSubscription subscription(BillingPlan plan) {
        TenantSubscription subscription = new TenantSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setTenantId(TENANT_ID);
        subscription.setBillingPlan(plan);
        subscription.setProvider(BillingProvider.ASAAS);
        subscription.setStatus(BillingSubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(LocalDateTime.now().minusDays(1));
        subscription.setCancelAtPeriodEnd(false);
        return subscription;
    }

    private TenantBillingPayment payment(String id, LocalDate dueDate) {
        TenantBillingPayment payment = new TenantBillingPayment();
        payment.setProviderPaymentId(id);
        payment.setTenantId(TENANT_ID);
        payment.setDueDate(dueDate);
        return payment;
    }

    private BillingPlan billingPlan(BillingPlanCode code) {
        BillingPlan plan = new BillingPlan();
        plan.setCode(code);
        plan.setPlanName(code.name());
        plan.setMonthlyPrice(BigDecimal.valueOf(99));
        plan.setSortOrder(code.ordinal());
        return plan;
    }
}
