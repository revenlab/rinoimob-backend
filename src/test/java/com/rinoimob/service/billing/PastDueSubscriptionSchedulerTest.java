package com.rinoimob.service.billing;

import com.rinoimob.domain.entity.BillingPlan;
import com.rinoimob.domain.entity.TenantSubscription;
import com.rinoimob.domain.enums.BillingPlanCode;
import com.rinoimob.domain.enums.BillingProvider;
import com.rinoimob.domain.enums.BillingSubscriptionStatus;
import com.rinoimob.domain.repository.TenantSubscriptionRepository;
import com.rinoimob.service.billing.payment.BillingGatewayPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PastDueSubscriptionSchedulerTest {

    @Mock private TenantSubscriptionRepository tenantSubscriptionRepository;
    @Mock private BillingPlanResolverService billingPlanResolverService;
    @Mock private TenantBillingProfileService tenantBillingProfileService;
    @Mock private BillingGatewayPort billingGatewayPort;

    @Test
    void downgradesSubscriptionAfterMoreThanSevenDaysPastDue() {
        TenantSubscription subscription = new TenantSubscription();
        subscription.setTenantId(UUID.randomUUID());
        subscription.setStatus(BillingSubscriptionStatus.PAST_DUE);
        subscription.setProvider(BillingProvider.ASAAS);
        subscription.setProviderSubscriptionId("sub_overdue");
        subscription.setPastDueAt(LocalDateTime.now().minusDays(8));
        BillingPlan freePlan = new BillingPlan();
        freePlan.setCode(BillingPlanCode.FREE);

        when(tenantSubscriptionRepository.findAllByStatusAndPastDueAtBefore(eq(BillingSubscriptionStatus.PAST_DUE), any()))
                .thenReturn(List.of(subscription));
        when(billingPlanResolverService.getRequiredGlobalPlan(BillingPlanCode.FREE)).thenReturn(freePlan);

        PastDueSubscriptionScheduler scheduler = new PastDueSubscriptionScheduler(
                tenantSubscriptionRepository, billingPlanResolverService, tenantBillingProfileService, billingGatewayPort
        );

        int downgraded = scheduler.downgradeSubscriptionsOverdueForMoreThanSevenDays();

        assertThat(downgraded).isEqualTo(1);
        verify(billingGatewayPort).cancelSubscription("sub_overdue");
        verify(tenantSubscriptionRepository).save(subscription);
        verify(tenantBillingProfileService).replaceProfileForPlan(
                subscription.getTenantId(), freePlan, null, "Downgraded to Free after more than 7 days past due"
        );
        assertThat(subscription.getBillingPlan()).isEqualTo(freePlan);
        assertThat(subscription.getProvider()).isEqualTo(BillingProvider.MANUAL);
        assertThat(subscription.getStatus()).isEqualTo(BillingSubscriptionStatus.CANCELED);
        assertThat(subscription.getPastDueAt()).isNull();
    }
}
