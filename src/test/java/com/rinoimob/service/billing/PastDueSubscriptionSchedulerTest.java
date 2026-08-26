package com.rinoimob.service.billing;

import com.rinoimob.domain.entity.BillingPlan;
import com.rinoimob.domain.entity.TenantSubscription;
import com.rinoimob.domain.enums.BillingPlanCode;
import com.rinoimob.domain.enums.BillingProvider;
import com.rinoimob.domain.enums.BillingProviderOperationType;
import com.rinoimob.domain.enums.BillingSubscriptionStatus;
import com.rinoimob.domain.repository.TenantSubscriptionChangeRepository;
import com.rinoimob.domain.repository.TenantSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PastDueSubscriptionSchedulerTest {

    @Mock private TenantSubscriptionRepository subscriptionRepository;
    @Mock private TenantSubscriptionChangeRepository changeRepository;
    @Mock private BillingPlanResolverService planResolverService;
    @Mock private TenantBillingProfileService profileService;
    @Mock private BillingProviderOperationService operationService;

    @Test
    void shouldSuspendAccessAfterSevenDaysWithoutLosingAsaasContract() {
        BillingPlan paidPlan = new BillingPlan();
        paidPlan.setCode(BillingPlanCode.PRIME);
        BillingPlan freePlan = new BillingPlan();
        freePlan.setCode(BillingPlanCode.FREE);
        TenantSubscription subscription = new TenantSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setTenantId(UUID.randomUUID());
        subscription.setBillingPlan(paidPlan);
        subscription.setStatus(BillingSubscriptionStatus.PAST_DUE);
        subscription.setProvider(BillingProvider.ASAAS);
        subscription.setProviderCustomerId("cus_keep");
        subscription.setProviderSubscriptionId("sub_keep");
        subscription.setPastDueAt(LocalDateTime.now().minusDays(8));

        when(subscriptionRepository.findAllByStatusAndPastDueAtBefore(eq(BillingSubscriptionStatus.PAST_DUE), any()))
                .thenReturn(List.of(subscription));
        when(planResolverService.getRequiredGlobalPlan(BillingPlanCode.FREE)).thenReturn(freePlan);

        PastDueSubscriptionScheduler scheduler = new PastDueSubscriptionScheduler(
                subscriptionRepository, changeRepository, planResolverService, profileService, operationService
        );

        assertThat(scheduler.downgradeSubscriptionsOverdueForMoreThanSevenDays()).isEqualTo(1);
        assertThat(subscription.getStatus()).isEqualTo(BillingSubscriptionStatus.SUSPENDED);
        assertThat(subscription.getBillingPlan()).isEqualTo(paidPlan);
        assertThat(subscription.getProviderCustomerId()).isEqualTo("cus_keep");
        assertThat(subscription.getProviderSubscriptionId()).isEqualTo("sub_keep");
        assertThat(subscription.getAccessRestrictedAt()).isNotNull();
        verify(profileService).replaceProfileForPlan(
                subscription.getTenantId(), freePlan, null, "Free limits after more than 7 days past due"
        );
        verify(operationService).enqueue(
                eq(subscription.getTenantId()), eq(BillingProviderOperationType.INACTIVATE_SUBSCRIPTION),
                eq("sub_keep"), eq("subscription:" + subscription.getId() + ":past-due-suspension"), anyMap()
        );
    }
}
