package com.rinoimob.service.billing;

import com.rinoimob.domain.entity.BillingPlan;
import com.rinoimob.domain.entity.TenantSubscription;
import com.rinoimob.domain.entity.TenantSubscriptionChange;
import com.rinoimob.domain.enums.BillingPlanCode;
import com.rinoimob.domain.enums.BillingProvider;
import com.rinoimob.domain.enums.BillingProviderOperationType;
import com.rinoimob.domain.enums.BillingSubscriptionChangeStatus;
import com.rinoimob.domain.enums.BillingSubscriptionStatus;
import com.rinoimob.domain.repository.TenantSubscriptionChangeRepository;
import com.rinoimob.domain.repository.TenantSubscriptionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PastDueSubscriptionScheduler {

    private static final int GRACE_PERIOD_DAYS = 7;

    private final TenantSubscriptionRepository subscriptionRepository;
    private final TenantSubscriptionChangeRepository changeRepository;
    private final BillingPlanResolverService planResolverService;
    private final TenantBillingProfileService profileService;
    private final BillingProviderOperationService operationService;

    public PastDueSubscriptionScheduler(TenantSubscriptionRepository subscriptionRepository,
                                        TenantSubscriptionChangeRepository changeRepository,
                                        BillingPlanResolverService planResolverService,
                                        TenantBillingProfileService profileService,
                                        BillingProviderOperationService operationService) {
        this.subscriptionRepository = subscriptionRepository;
        this.changeRepository = changeRepository;
        this.planResolverService = planResolverService;
        this.profileService = profileService;
        this.operationService = operationService;
    }

    @Scheduled(fixedDelayString = "${billing.asaas.past-due-scan-interval-ms:3600000}")
    @Transactional
    public void enforceBillingLifecycle() {
        downgradeSubscriptionsOverdueForMoreThanSevenDays();
        applyScheduledPlanChanges();
        finalizeScheduledCancellations();
    }

    @Transactional
    public int downgradeSubscriptionsOverdueForMoreThanSevenDays() {
        LocalDateTime now = LocalDateTime.now();
        List<TenantSubscription> overdue = subscriptionRepository.findAllByStatusAndPastDueAtBefore(
                BillingSubscriptionStatus.PAST_DUE, now.minusDays(GRACE_PERIOD_DAYS)
        );
        if (overdue.isEmpty()) {
            return 0;
        }
        BillingPlan freePlan = planResolverService.getRequiredGlobalPlan(BillingPlanCode.FREE);
        for (TenantSubscription subscription : overdue) {
            subscription.setStatus(BillingSubscriptionStatus.SUSPENDED);
            subscription.setAccessRestrictedAt(now);
            subscription.setSuspensionReason("PAYMENT_OVERDUE");
            subscriptionRepository.save(subscription);
            profileService.replaceProfileForPlan(
                    subscription.getTenantId(), freePlan, null, "Free limits after more than 7 days past due"
            );
            operationService.enqueue(
                    subscription.getTenantId(), BillingProviderOperationType.INACTIVATE_SUBSCRIPTION,
                    subscription.getProviderSubscriptionId(),
                    "subscription:" + subscription.getId() + ":past-due-suspension", Map.of()
            );
        }
        return overdue.size();
    }

    @Transactional
    public int applyScheduledPlanChanges() {
        LocalDateTime now = LocalDateTime.now();
        List<TenantSubscriptionChange> changes = changeRepository.findAllByStatusAndEffectiveAtBefore(
                BillingSubscriptionChangeStatus.SCHEDULED, now
        );
        int applied = 0;
        for (TenantSubscriptionChange change : changes) {
            TenantSubscription subscription = subscriptionRepository.findByTenantIdForUpdate(change.getTenantId()).orElse(null);
            if (subscription == null) {
                change.setStatus(BillingSubscriptionChangeStatus.FAILED);
                change.setFailureReason("Tenant subscription not found");
                changeRepository.save(change);
                continue;
            }
            BillingPlan targetPlan = change.getTargetPlan();
            if (targetPlan.getCode() == BillingPlanCode.FREE) {
                operationService.enqueue(
                        subscription.getTenantId(), BillingProviderOperationType.INACTIVATE_SUBSCRIPTION,
                        subscription.getProviderSubscriptionId(), "change:" + change.getId() + ":inactivate",
                        Map.of()
                );
                subscription.setStatus(BillingSubscriptionStatus.CANCELED);
                subscription.setEndedAt(now);
            } else if (subscription.getProviderSubscriptionId() != null) {
                LocalDate nextDueDate = now.toLocalDate().plusMonths(1);
                operationService.enqueue(
                        subscription.getTenantId(), BillingProviderOperationType.UPDATE_SUBSCRIPTION,
                        subscription.getProviderSubscriptionId(), "change:" + change.getId() + ":update-plan",
                        Map.of("value", targetPlan.getMonthlyPrice().toPlainString(), "nextDueDate", nextDueDate.toString())
                );
                subscription.setStatus(BillingSubscriptionStatus.ACTIVE);
            }
            subscription.setBillingPlan(targetPlan);
            subscription.setLastPlanChangeAt(now);
            subscription.setCancelAtPeriodEnd(false);
            subscriptionRepository.save(subscription);
            profileService.replaceProfileForPlan(
                    subscription.getTenantId(), targetPlan, null, "Scheduled billing plan change applied"
            );
            change.setStatus(BillingSubscriptionChangeStatus.APPLIED);
            change.setAppliedAt(now);
            changeRepository.save(change);
            applied++;
        }
        return applied;
    }

    @Transactional
    public int finalizeScheduledCancellations() {
        LocalDateTime now = LocalDateTime.now();
        List<TenantSubscription> subscriptions = subscriptionRepository
                .findAllByCancelAtPeriodEndTrueAndCurrentPeriodEndBefore(now);
        if (subscriptions.isEmpty()) {
            return 0;
        }
        BillingPlan freePlan = planResolverService.getRequiredGlobalPlan(BillingPlanCode.FREE);
        for (TenantSubscription subscription : subscriptions) {
            subscription.setBillingPlan(freePlan);
            subscription.setProvider(BillingProvider.MANUAL);
            subscription.setStatus(BillingSubscriptionStatus.CANCELED);
            subscription.setCancelAtPeriodEnd(false);
            subscription.setEndedAt(now);
            subscription.setLastPlanChangeAt(now);
            subscriptionRepository.save(subscription);
            profileService.replaceProfileForPlan(
                    subscription.getTenantId(), freePlan, null, "Voluntary cancellation reached period end"
            );
        }
        return subscriptions.size();
    }
}
