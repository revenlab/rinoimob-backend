package com.rinoimob.service.billing;

import com.rinoimob.domain.dto.BillingCustomerDetailsResponse;
import com.rinoimob.domain.dto.BillingStatusResponse;
import com.rinoimob.domain.dto.StartBillingCheckoutRequest;
import com.rinoimob.domain.dto.StartBillingCheckoutResponse;
import com.rinoimob.domain.dto.billing.TenantBillingLimitsSnapshot;
import com.rinoimob.domain.entity.BillingPlan;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.entity.TenantSubscription;
import com.rinoimob.domain.entity.TenantSubscriptionChange;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.enums.BillingPlanCode;
import com.rinoimob.domain.enums.BillingProvider;
import com.rinoimob.domain.enums.BillingSubscriptionChangeStatus;
import com.rinoimob.domain.enums.BillingSubscriptionStatus;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.TenantSubscriptionChangeRepository;
import com.rinoimob.domain.repository.TenantSubscriptionRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.billing.payment.BillingGatewayPort;
import com.rinoimob.service.billing.payment.dto.BillingCheckoutRequest;
import com.rinoimob.service.billing.payment.dto.BillingCheckoutResult;
import com.rinoimob.service.billing.payment.dto.BillingCustomerResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantBillingPortalServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock private TenantSubscriptionService subscriptionService;
    @Mock private TenantSubscriptionRepository subscriptionRepository;
    @Mock private TenantSubscriptionChangeRepository changeRepository;
    @Mock private TenantBillingProfileService profileService;
    @Mock private BillingPlanResolverService planResolverService;
    @Mock private TenantBillingPaymentService paymentService;
    @Mock private BillingProviderOperationService operationService;
    @Mock private BillingGatewayPort billingGatewayPort;
    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;

    private TenantBillingPortalService service;

    @BeforeEach
    void setUp() {
        service = serviceWithCallback("https://app.example.com");
    }

    @Test
    void shouldExposeEffectiveFeatureAccessInLightweightBillingStatus() {
        BillingPlan currentPlan = billingPlan(BillingPlanCode.STARTER, 2, 49);
        TenantSubscription subscription = subscription(currentPlan);
        when(subscriptionService.ensureFreeSubscriptionForTenant(TENANT_ID)).thenReturn(subscription);
        when(profileService.resolveEffectiveLimits(TENANT_ID)).thenReturn(new TenantBillingLimitsSnapshot(
                TENANT_ID,
                BillingPlanCode.STARTER,
                "Starter",
                5,
                100,
                500,
                5,
                true,
                true,
                false,
                false,
                false,
                false
        ));

        BillingStatusResponse response = service.getBillingStatus(TENANT_ID);

        assertThat(response.currentPlanCode()).isEqualTo(BillingPlanCode.STARTER);
        assertThat(response.blogEnabled()).isTrue();
        assertThat(response.customDomainEnabled()).isTrue();
        assertThat(response.automationCrmEnabled()).isFalse();
        assertThat(response.publicApiEnabled()).isFalse();
    }

    @Test
    void shouldKeepCurrentSubscriptionActiveWhileUpgradeCheckoutIsPending() {
        BillingPlan currentPlan = billingPlan(BillingPlanCode.STARTER, 2, 49);
        BillingPlan targetPlan = billingPlan(BillingPlanCode.PRIME, 3, 99);
        TenantSubscription subscription = subscription(currentPlan);
        subscription.setProviderSubscriptionId("sub_old");

        mockTenantAndUser();
        mockCompleteCustomer();
        mockSubscription(subscription);
        when(planResolverService.getRequiredGlobalPlan(BillingPlanCode.PRIME)).thenReturn(targetPlan);
        when(billingGatewayPort.createCheckout(any())).thenReturn(
                new BillingCheckoutResult("checkout_123", "https://checkout.example", "cus_1", null, "2026-08-25T23:59:00Z")
        );

        StartBillingCheckoutResponse response = service.startCheckout(
                TENANT_ID, USER_ID, new StartBillingCheckoutRequest(BillingPlanCode.PRIME)
        );

        assertThat(response.requiresCheckout()).isTrue();
        assertThat(response.checkoutId()).isEqualTo("checkout_123");
        assertThat(subscription.getBillingPlan()).isEqualTo(currentPlan);
        assertThat(subscription.getStatus()).isEqualTo(BillingSubscriptionStatus.ACTIVE);
        verify(billingGatewayPort, never()).cancelSubscription(any());

        ArgumentCaptor<TenantSubscriptionChange> changeCaptor = ArgumentCaptor.forClass(TenantSubscriptionChange.class);
        verify(changeRepository).save(changeCaptor.capture());
        assertThat(changeCaptor.getValue().getStatus()).isEqualTo(BillingSubscriptionChangeStatus.PENDING);
        assertThat(changeCaptor.getValue().getTargetPlan()).isEqualTo(targetPlan);
        assertThat(changeCaptor.getValue().getPreviousProviderSubscriptionId()).isEqualTo("sub_old");

        ArgumentCaptor<BillingCheckoutRequest> checkoutCaptor = ArgumentCaptor.forClass(BillingCheckoutRequest.class);
        verify(billingGatewayPort).createCheckout(checkoutCaptor.capture());
        assertThat(checkoutCaptor.getValue().successUrl()).isEqualTo("https://app.example.com/meu-plano?billing=success");
    }

    @Test
    void shouldScheduleDowngradeForCurrentPeriodEndWithoutCreatingCheckout() {
        BillingPlan currentPlan = billingPlan(BillingPlanCode.PRIME, 3, 99);
        BillingPlan targetPlan = billingPlan(BillingPlanCode.STARTER, 2, 49);
        TenantSubscription subscription = subscription(currentPlan);
        subscription.setCurrentPeriodEnd(LocalDateTime.of(2026, 9, 25, 0, 0));

        mockTenantAndUser();
        mockSubscription(subscription);
        when(planResolverService.getRequiredGlobalPlan(BillingPlanCode.STARTER)).thenReturn(targetPlan);

        StartBillingCheckoutResponse response = service.startCheckout(
                TENANT_ID, USER_ID, new StartBillingCheckoutRequest(BillingPlanCode.STARTER)
        );

        assertThat(response.requiresCheckout()).isFalse();
        assertThat(response.changeStatus()).isEqualTo("SCHEDULED");
        assertThat(response.effectiveAt()).isEqualTo(subscription.getCurrentPeriodEnd());
        verify(billingGatewayPort, never()).createCheckout(any());
        verify(billingGatewayPort, never()).cancelSubscription(any());
    }

    @Test
    void shouldRejectUpgradeWhenHttpsCallbackIsMissingBeforeCreatingCustomer() {
        service = serviceWithCallback("");
        BillingPlan currentPlan = billingPlan(BillingPlanCode.FREE, 1, 0);
        BillingPlan targetPlan = billingPlan(BillingPlanCode.STARTER, 2, 49);
        TenantSubscription subscription = subscription(currentPlan);

        mockTenantAndUser();
        mockSubscription(subscription);
        when(planResolverService.getRequiredGlobalPlan(BillingPlanCode.STARTER)).thenReturn(targetPlan);

        assertThatThrownBy(() -> service.startCheckout(
                TENANT_ID, USER_ID, new StartBillingCheckoutRequest(BillingPlanCode.STARTER)
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        verify(billingGatewayPort, never()).createOrUpdateCustomer(any(), any());
    }

    @Test
    void shouldBlockSecondOpenPlanChange() {
        BillingPlan currentPlan = billingPlan(BillingPlanCode.STARTER, 2, 49);
        TenantSubscription subscription = subscription(currentPlan);
        TenantSubscriptionChange pending = new TenantSubscriptionChange();

        mockTenantAndUser();
        when(subscriptionService.ensureFreeSubscriptionForTenant(TENANT_ID)).thenReturn(subscription);
        when(subscriptionRepository.findByTenantIdForUpdate(TENANT_ID)).thenReturn(Optional.of(subscription));
        when(planResolverService.getRequiredGlobalPlan(BillingPlanCode.PRIME))
                .thenReturn(billingPlan(BillingPlanCode.PRIME, 3, 99));
        when(changeRepository.findFirstByTenantIdAndStatusInOrderByCreatedAtDesc(eq(TENANT_ID), any()))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.startCheckout(
                TENANT_ID, USER_ID, new StartBillingCheckoutRequest(BillingPlanCode.PRIME)
        )).isInstanceOf(ResponseStatusException.class).hasMessageContaining("already pending");
    }

    private TenantBillingPortalService serviceWithCallback(String callback) {
        return new TenantBillingPortalService(
                subscriptionService, subscriptionRepository, changeRepository, profileService, planResolverService,
                paymentService, operationService, billingGatewayPort, userRepository, tenantRepository, callback, false
        );
    }

    private void mockTenantAndUser() {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setName("Tenant Name");
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("owner@example.com");
        user.setFirstName("Owner");
        user.setLastName("User");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private void mockSubscription(TenantSubscription subscription) {
        when(subscriptionService.ensureFreeSubscriptionForTenant(TENANT_ID)).thenReturn(subscription);
        when(subscriptionRepository.findByTenantIdForUpdate(TENANT_ID)).thenReturn(Optional.of(subscription));
        when(changeRepository.findFirstByTenantIdAndStatusInOrderByCreatedAtDesc(eq(TENANT_ID), any()))
                .thenReturn(Optional.empty());
    }

    private void mockCompleteCustomer() {
        when(profileService.getCustomerDetails(eq(TENANT_ID), any())).thenReturn(
                new BillingCustomerDetailsResponse(
                        "24971563792", "47999999999", "Rua Teste", "10", null, "89223005", "Centro", true
                )
        );
        when(profileService.getProviderCustomerId(TENANT_ID)).thenReturn("cus_existing");
        when(billingGatewayPort.createOrUpdateCustomer(any(), any())).thenReturn(
                new BillingCustomerResult("cus_existing", "owner@example.com", "Owner User")
        );
    }

    private TenantSubscription subscription(BillingPlan plan) {
        TenantSubscription subscription = new TenantSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setTenantId(TENANT_ID);
        subscription.setBillingPlan(plan);
        subscription.setStatus(BillingSubscriptionStatus.ACTIVE);
        subscription.setProvider(plan.getCode() == BillingPlanCode.FREE ? BillingProvider.MANUAL : BillingProvider.ASAAS);
        subscription.setProviderCustomerId("cus_existing");
        subscription.setCancelAtPeriodEnd(false);
        return subscription;
    }

    private BillingPlan billingPlan(BillingPlanCode code, int sortOrder, int monthlyPrice) {
        BillingPlan plan = new BillingPlan();
        plan.setCode(code);
        plan.setPlanName(code.name());
        plan.setSortOrder(sortOrder);
        plan.setMonthlyPrice(BigDecimal.valueOf(monthlyPrice));
        return plan;
    }
}
