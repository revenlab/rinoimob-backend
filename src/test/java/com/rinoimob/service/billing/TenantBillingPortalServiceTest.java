package com.rinoimob.service.billing;

import com.rinoimob.domain.dto.StartBillingCheckoutRequest;
import com.rinoimob.domain.dto.StartBillingCheckoutResponse;
import com.rinoimob.domain.entity.BillingPlan;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.entity.TenantSubscription;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.enums.BillingPlanCode;
import com.rinoimob.domain.enums.BillingProvider;
import com.rinoimob.domain.enums.BillingSubscriptionStatus;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.TenantSubscriptionRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.billing.payment.BillingGatewayPort;
import com.rinoimob.service.billing.payment.dto.BillingCheckoutRequest;
import com.rinoimob.service.billing.payment.dto.BillingCheckoutResult;
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

    @Mock
    private TenantSubscriptionService tenantSubscriptionService;
    @Mock
    private TenantSubscriptionRepository tenantSubscriptionRepository;
    @Mock
    private TenantBillingProfileService tenantBillingProfileService;
    @Mock
    private BillingPlanResolverService billingPlanResolverService;
    @Mock
    private BillingGatewayPort billingGatewayPort;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantRepository tenantRepository;

    private TenantBillingPortalService service;

    @BeforeEach
    void setUp() {
        service = new TenantBillingPortalService(
                tenantSubscriptionService,
                tenantSubscriptionRepository,
                tenantBillingProfileService,
                billingPlanResolverService,
                billingGatewayPort,
                userRepository,
                tenantRepository
        );
    }

    @Test
    void startCheckout_throwsConflict_whenDowngradeBefore31DaysCooldown() {
        BillingPlan currentPlan = billingPlan(BillingPlanCode.PRIME, 3, 99);
        BillingPlan targetPlan = billingPlan(BillingPlanCode.STARTER, 2, 49);
        TenantSubscription subscription = subscription(currentPlan, LocalDateTime.now().minusDays(10));

        mockTenantAndUser();
        when(tenantSubscriptionService.ensureFreeSubscriptionForTenant(TENANT_ID)).thenReturn(subscription);
        when(billingPlanResolverService.getRequiredGlobalPlan(BillingPlanCode.STARTER)).thenReturn(targetPlan);

        assertThatThrownBy(() -> service.startCheckout(
                TENANT_ID,
                USER_ID,
                new StartBillingCheckoutRequest(BillingPlanCode.STARTER, null, null)
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("31 days");

        verify(billingGatewayPort, never()).createCheckout(any());
        verify(billingGatewayPort, never()).cancelSubscription(any());
    }

    @Test
    void startCheckout_allowsUpgradeAnytime_andCancelsPreviousProviderSubscription() {
        BillingPlan currentPlan = billingPlan(BillingPlanCode.STARTER, 2, 49);
        BillingPlan targetPlan = billingPlan(BillingPlanCode.PRIME, 3, 99);
        TenantSubscription subscription = subscription(currentPlan, LocalDateTime.now().minusDays(1));
        subscription.setProviderSubscriptionId("subs_old_active");

        mockTenantAndUser();
        when(tenantSubscriptionService.ensureFreeSubscriptionForTenant(TENANT_ID)).thenReturn(subscription);
        when(billingPlanResolverService.getRequiredGlobalPlan(BillingPlanCode.PRIME)).thenReturn(targetPlan);
        when(billingGatewayPort.createCheckout(any())).thenReturn(
                new BillingCheckoutResult("chk_123", "https://checkout.example", "cus_1", "subs_new", "2026-12-31T00:00:00Z")
        );

        StartBillingCheckoutResponse response = service.startCheckout(
                TENANT_ID,
                USER_ID,
                new StartBillingCheckoutRequest(BillingPlanCode.PRIME, null, null)
        );

        assertThat(response.checkoutId()).isEqualTo("chk_123");
        verify(billingGatewayPort).cancelSubscription(eq("subs_old_active"));
        ArgumentCaptor<BillingCheckoutRequest> checkoutCaptor = ArgumentCaptor.forClass(BillingCheckoutRequest.class);
        verify(billingGatewayPort).createCheckout(checkoutCaptor.capture());
        assertThat(checkoutCaptor.getValue().externalId()).isNotBlank();
        assertThat(checkoutCaptor.getValue().externalId()).isNotEqualTo(TENANT_ID.toString());

        ArgumentCaptor<TenantSubscription> captor = ArgumentCaptor.forClass(TenantSubscription.class);
        verify(tenantSubscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BillingSubscriptionStatus.PENDING);
        assertThat(captor.getValue().getProvider()).isEqualTo(BillingProvider.ASAAS);
        assertThat(captor.getValue().getProviderSubscriptionId()).isNull();
    }

    @Test
    void startCheckout_allowsDowngradeAfter31Days_andCancelsPreviousProviderSubscription() {
        BillingPlan currentPlan = billingPlan(BillingPlanCode.PRIME, 3, 99);
        BillingPlan targetPlan = billingPlan(BillingPlanCode.STARTER, 2, 49);
        TenantSubscription subscription = subscription(currentPlan, LocalDateTime.now().minusDays(40));
        subscription.setProviderSubscriptionId("subs_old_active");

        mockTenantAndUser();
        when(tenantSubscriptionService.ensureFreeSubscriptionForTenant(TENANT_ID)).thenReturn(subscription);
        when(billingPlanResolverService.getRequiredGlobalPlan(BillingPlanCode.STARTER)).thenReturn(targetPlan);
        when(billingGatewayPort.createCheckout(any())).thenReturn(
                new BillingCheckoutResult("chk_456", "https://checkout.example", "cus_1", "subs_new", "2026-12-31T00:00:00Z")
        );

        StartBillingCheckoutResponse response = service.startCheckout(
                TENANT_ID,
                USER_ID,
                new StartBillingCheckoutRequest(BillingPlanCode.STARTER, null, null)
        );

        assertThat(response.checkoutId()).isEqualTo("chk_456");
        verify(billingGatewayPort).cancelSubscription(eq("subs_old_active"));
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

    private TenantSubscription subscription(BillingPlan currentPlan, LocalDateTime lastPlanChangeAt) {
        TenantSubscription subscription = new TenantSubscription();
        subscription.setTenantId(TENANT_ID);
        subscription.setBillingPlan(currentPlan);
        subscription.setStatus(BillingSubscriptionStatus.ACTIVE);
        subscription.setProvider(BillingProvider.ASAAS);
        subscription.setProviderCustomerId("cus_existing");
        subscription.setLastPlanChangeAt(lastPlanChangeAt);
        return subscription;
    }

    private BillingPlan billingPlan(BillingPlanCode code, int sortOrder, int monthlyPrice) {
        BillingPlan plan = new BillingPlan();
        plan.setCode(code);
        plan.setSortOrder(sortOrder);
        plan.setMonthlyPrice(BigDecimal.valueOf(monthlyPrice));
        return plan;
    }
}
