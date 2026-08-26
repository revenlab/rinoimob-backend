package com.rinoimob.service.billing;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AsaasWebhookService {

    private static final Logger log = LoggerFactory.getLogger(AsaasWebhookService.class);
    private static final DateTimeFormatter ASAAS_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final TenantSubscriptionRepository subscriptionRepository;
    private final TenantSubscriptionChangeRepository changeRepository;
    private final TenantBillingProfileRepository profileRepository;
    private final BillingPlanResolverService billingPlanResolverService;
    private final TenantBillingProfileService tenantBillingProfileService;
    private final TenantBillingPaymentService paymentService;
    private final BillingProviderOperationService operationService;

    public AsaasWebhookService(TenantSubscriptionRepository subscriptionRepository,
                               TenantSubscriptionChangeRepository changeRepository,
                               TenantBillingProfileRepository profileRepository,
                               BillingPlanResolverService billingPlanResolverService,
                               TenantBillingProfileService tenantBillingProfileService,
                               TenantBillingPaymentService paymentService,
                               BillingProviderOperationService operationService) {
        this.subscriptionRepository = subscriptionRepository;
        this.changeRepository = changeRepository;
        this.profileRepository = profileRepository;
        this.billingPlanResolverService = billingPlanResolverService;
        this.tenantBillingProfileService = tenantBillingProfileService;
        this.paymentService = paymentService;
        this.operationService = operationService;
    }

    public boolean supports(String event) {
        return event != null && (event.startsWith("PAYMENT_")
                || event.equals("CHECKOUT_PAID")
                || event.equals("CHECKOUT_CANCELED")
                || event.equals("CHECKOUT_EXPIRED")
                || event.equals("SUBSCRIPTION_CREATED")
                || event.equals("SUBSCRIPTION_UPDATED")
                || event.equals("SUBSCRIPTION_INACTIVATED")
                || event.equals("SUBSCRIPTION_DELETED"));
    }

    @Transactional
    public UUID handleWebhook(JsonNode payload) {
        String event = text(payload, "event");
        if (!supports(event)) {
            return null;
        }

        JsonNode checkout = payload.path("checkout");
        JsonNode payment = payload.path("payment");
        JsonNode providerSubscription = payload.path("subscription");
        String externalReference = firstNonBlank(text(checkout, "externalReference"), text(payment, "externalReference"),
                text(providerSubscription, "externalReference"));
        String checkoutId = firstNonBlank(text(checkout, "id"), text(payment, "checkoutSession"));
        String providerSubscriptionId = firstNonBlank(text(payment, "subscription"), text(providerSubscription, "id"));
        String providerCustomerId = firstNonBlank(text(checkout, "customer"), text(payment, "customer"),
                text(providerSubscription, "customer"));

        TenantSubscriptionChange change = resolveChange(externalReference, checkoutId);
        TenantSubscription subscription = resolveSubscription(change, externalReference, providerSubscriptionId,
                checkoutId, providerCustomerId);
        if (subscription == null) {
            throw new IllegalStateException("Could not correlate Asaas event " + event);
        }
        UUID tenantId = subscription.getTenantId();
        if (providerCustomerId != null) {
            tenantBillingProfileService.saveProviderCustomerId(tenantId, subscription.getBillingPlan(), providerCustomerId);
            subscription.setProviderCustomerId(providerCustomerId);
        }

        if (event.startsWith("CHECKOUT_")) {
            handleCheckoutEvent(event, change);
            subscriptionRepository.save(subscription);
            return tenantId;
        }

        if (event.startsWith("SUBSCRIPTION_")) {
            handleSubscriptionEvent(event, subscription, providerSubscriptionId);
            return tenantId;
        }

        LocalDateTime eventAt = parseDateTime(text(payload, "dateCreated"));
        TenantBillingPayment billingPayment = paymentService.upsert(tenantId, event, payment, eventAt);
        String paymentSubscriptionId = text(payment, "subscription");
        if (paymentSubscriptionId != null && subscription.getProviderSubscriptionId() != null
                && !paymentSubscriptionId.equals(subscription.getProviderSubscriptionId())
                && change == null) {
            log.info("Stored historical payment event {} without changing current subscription for tenant {}", event, tenantId);
            return tenantId;
        }

        switch (event) {
            case "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED" -> {
                if (change != null && change.getStatus() != BillingSubscriptionChangeStatus.SCHEDULED) {
                    activateChange(subscription, change, payment);
                } else {
                    restorePaidSubscription(subscription, payment);
                }
            }
            case "PAYMENT_OVERDUE" -> markPastDue(subscription, payment);
            case "PAYMENT_REFUNDED", "PAYMENT_PARTIALLY_REFUNDED",
                 "PAYMENT_CHARGEBACK_REQUESTED", "PAYMENT_CHARGEBACK_DISPUTE",
                 "PAYMENT_AWAITING_CHARGEBACK_REVERSAL" -> revokeCurrentPeriod(subscription, billingPayment, event);
            case "PAYMENT_DELETED" -> revokeCurrentPeriod(subscription, billingPayment, event);
            default -> subscriptionRepository.save(subscription);
        }
        return tenantId;
    }

    private void handleCheckoutEvent(String event, TenantSubscriptionChange change) {
        if (change == null) {
            throw new IllegalStateException("Asaas checkout event has no local subscription change");
        }
        if (change.getStatus() == BillingSubscriptionChangeStatus.APPLIED) {
            return;
        }
        switch (event) {
            case "CHECKOUT_PAID" -> {
                change.setStatus(BillingSubscriptionChangeStatus.PAID);
                change.setPaidAt(LocalDateTime.now());
            }
            case "CHECKOUT_CANCELED" -> change.setStatus(BillingSubscriptionChangeStatus.CANCELED);
            case "CHECKOUT_EXPIRED" -> change.setStatus(BillingSubscriptionChangeStatus.EXPIRED);
            default -> {
                return;
            }
        }
        changeRepository.save(change);
    }

    private void handleSubscriptionEvent(String event, TenantSubscription subscription, String providerSubscriptionId) {
        if (providerSubscriptionId != null && subscription.getProviderSubscriptionId() == null) {
            subscription.setProviderSubscriptionId(providerSubscriptionId);
        }
        if (event.equals("SUBSCRIPTION_DELETED") && !Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd())) {
            BillingPlan freePlan = billingPlanResolverService.getRequiredGlobalPlan(BillingPlanCode.FREE);
            subscription.setStatus(BillingSubscriptionStatus.CANCELED);
            subscription.setEndedAt(LocalDateTime.now());
            tenantBillingProfileService.replaceProfileForPlan(
                    subscription.getTenantId(), freePlan, null, "Asaas subscription deleted"
            );
        }
        subscriptionRepository.save(subscription);
    }

    private void activateChange(TenantSubscription subscription, TenantSubscriptionChange change, JsonNode payment) {
        if (change.getStatus() == BillingSubscriptionChangeStatus.APPLIED) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String newSubscriptionId = firstNonBlank(text(payment, "subscription"), change.getNewProviderSubscriptionId());
        subscription.setBillingPlan(change.getTargetPlan());
        subscription.setProvider(BillingProvider.ASAAS);
        subscription.setStatus(BillingSubscriptionStatus.ACTIVE);
        subscription.setProviderCheckoutId(change.getProviderCheckoutId());
        subscription.setProviderSubscriptionId(newSubscriptionId);
        subscription.setProviderInvoiceUrl(text(payment, "invoiceUrl"));
        LocalDateTime dueDate = parseDateTime(text(payment, "dueDate"));
        subscription.setPaymentDueDate(dueDate);
        subscription.setStartedAt(firstNonNull(subscription.getStartedAt(), now));
        subscription.setCurrentPeriodStart(firstNonNull(parseDateTime(text(payment, "dateCreated")), now));
        subscription.setCurrentPeriodEnd(dueDate == null ? now.plusMonths(1) : dueDate.plusMonths(1));
        subscription.setLastPlanChangeAt(now);
        subscription.setPastDueAt(null);
        subscription.setAccessRestrictedAt(null);
        subscription.setSuspensionReason(null);
        subscription.setEndedAt(null);
        subscription.setCancelAtPeriodEnd(false);
        subscriptionRepository.save(subscription);

        change.setNewProviderSubscriptionId(newSubscriptionId);
        change.setStatus(BillingSubscriptionChangeStatus.APPLIED);
        change.setAppliedAt(now);
        changeRepository.save(change);
        tenantBillingProfileService.replaceProfileForPlan(
                subscription.getTenantId(), change.getTargetPlan(), null, "Paid Asaas plan change applied"
        );
        String previousSubscriptionId = change.getPreviousProviderSubscriptionId();
        if (previousSubscriptionId != null && !previousSubscriptionId.equals(newSubscriptionId)) {
            operationService.enqueue(
                    subscription.getTenantId(), BillingProviderOperationType.CANCEL_SUBSCRIPTION,
                    previousSubscriptionId, "change:" + change.getId() + ":cancel-previous", Map.of()
            );
        }
    }

    private void restorePaidSubscription(TenantSubscription subscription, JsonNode payment) {
        LocalDateTime now = LocalDateTime.now();
        boolean wasSuspended = subscription.getStatus() == BillingSubscriptionStatus.SUSPENDED;
        subscription.setProvider(BillingProvider.ASAAS);
        subscription.setStatus(BillingSubscriptionStatus.ACTIVE);
        subscription.setProviderSubscriptionId(firstNonBlank(text(payment, "subscription"), subscription.getProviderSubscriptionId()));
        subscription.setProviderCheckoutId(firstNonBlank(text(payment, "checkoutSession"), subscription.getProviderCheckoutId()));
        subscription.setProviderInvoiceUrl(firstNonBlank(text(payment, "invoiceUrl"), subscription.getProviderInvoiceUrl()));
        LocalDateTime dueDate = parseDateTime(text(payment, "dueDate"));
        subscription.setPaymentDueDate(firstNonNull(dueDate, subscription.getPaymentDueDate()));
        subscription.setCurrentPeriodStart(firstNonNull(parseDateTime(text(payment, "dateCreated")), now));
        subscription.setCurrentPeriodEnd(dueDate == null ? now.plusMonths(1) : dueDate.plusMonths(1));
        subscription.setPastDueAt(null);
        subscription.setAccessRestrictedAt(null);
        subscription.setSuspensionReason(null);
        subscription.setEndedAt(null);
        subscription.setCancelAtPeriodEnd(false);
        subscriptionRepository.save(subscription);
        tenantBillingProfileService.replaceProfileForPlan(
                subscription.getTenantId(), subscription.getBillingPlan(), null, "Asaas payment restored paid access"
        );
        if (wasSuspended && subscription.getProviderSubscriptionId() != null) {
            LocalDate nextDueDate = nextFutureMonthlyDate(dueDate == null ? LocalDate.now() : dueDate.toLocalDate());
            operationService.enqueue(
                    subscription.getTenantId(), BillingProviderOperationType.REACTIVATE_SUBSCRIPTION,
                    subscription.getProviderSubscriptionId(),
                    "payment:" + text(payment, "id") + ":reactivate",
                    Map.of("nextDueDate", nextDueDate.toString())
            );
        }
    }

    private void markPastDue(TenantSubscription subscription, JsonNode payment) {
        LocalDateTime dueDate = parseDateTime(text(payment, "dueDate"));
        subscription.setStatus(BillingSubscriptionStatus.PAST_DUE);
        subscription.setPastDueAt(firstNonNull(dueDate, subscription.getPastDueAt(), LocalDateTime.now()));
        subscription.setPaymentDueDate(firstNonNull(dueDate, subscription.getPaymentDueDate()));
        subscription.setProviderInvoiceUrl(firstNonBlank(text(payment, "invoiceUrl"), subscription.getProviderInvoiceUrl()));
        subscriptionRepository.save(subscription);
    }

    private void revokeCurrentPeriod(TenantSubscription subscription, TenantBillingPayment payment, String event) {
        if (payment != null && payment.getDueDate() != null && subscription.getCurrentPeriodStart() != null
                && payment.getDueDate().isBefore(subscription.getCurrentPeriodStart().toLocalDate())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        BillingPlan freePlan = billingPlanResolverService.getRequiredGlobalPlan(BillingPlanCode.FREE);
        subscription.setStatus(BillingSubscriptionStatus.CANCELED);
        subscription.setAccessRestrictedAt(now);
        subscription.setSuspensionReason(event);
        subscription.setEndedAt(now);
        subscription.setPastDueAt(null);
        subscriptionRepository.save(subscription);
        tenantBillingProfileService.replaceProfileForPlan(
                subscription.getTenantId(), freePlan, null, "Paid access revoked by Asaas event: " + event
        );
        operationService.enqueue(
                subscription.getTenantId(), BillingProviderOperationType.INACTIVATE_SUBSCRIPTION,
                subscription.getProviderSubscriptionId(), "subscription:" + subscription.getId() + ":" + event,
                Map.of()
        );
    }

    private TenantSubscriptionChange resolveChange(String externalReference, String checkoutId) {
        if (externalReference != null) {
            TenantSubscriptionChange change = changeRepository.findByExternalReference(externalReference).orElse(null);
            if (change != null) {
                return change;
            }
        }
        return checkoutId == null ? null : changeRepository.findByProviderCheckoutId(checkoutId).orElse(null);
    }

    private TenantSubscription resolveSubscription(TenantSubscriptionChange change, String externalReference,
                                                   String providerSubscriptionId, String checkoutId,
                                                   String providerCustomerId) {
        if (change != null) {
            return subscriptionRepository.findByTenantIdForUpdate(change.getTenantId())
                    .orElseGet(() -> createFreeSubscription(change.getTenantId()));
        }
        if (providerSubscriptionId != null) {
            TenantSubscription subscription = subscriptionRepository.findByProviderSubscriptionId(providerSubscriptionId).orElse(null);
            if (subscription != null) {
                return subscriptionRepository.findByTenantIdForUpdate(subscription.getTenantId()).orElse(subscription);
            }
        }
        if (checkoutId != null) {
            TenantSubscriptionChange checkoutChange = changeRepository.findByProviderCheckoutId(checkoutId).orElse(null);
            if (checkoutChange != null) {
                return subscriptionRepository.findByTenantIdForUpdate(checkoutChange.getTenantId()).orElse(null);
            }
        }
        if (externalReference != null) {
            UUID tenantId = resolveTenantId(externalReference);
            return subscriptionRepository.findByTenantIdForUpdate(tenantId).orElseGet(() -> createFreeSubscription(tenantId));
        }
        if (providerCustomerId != null) {
            TenantBillingProfile profile = profileRepository.findByProviderCustomerId(providerCustomerId).orElse(null);
            if (profile != null) {
                return subscriptionRepository.findByTenantIdForUpdate(profile.getTenantId()).orElse(null);
            }
            List<TenantSubscription> legacyMatches = subscriptionRepository.findAllByProviderCustomerId(providerCustomerId);
            if (legacyMatches.size() == 1) {
                return subscriptionRepository.findByTenantIdForUpdate(legacyMatches.get(0).getTenantId())
                        .orElse(legacyMatches.get(0));
            }
        }
        return null;
    }

    private TenantSubscription createFreeSubscription(UUID tenantId) {
        BillingPlan free = billingPlanResolverService.getRequiredGlobalPlan(BillingPlanCode.FREE);
        TenantSubscription subscription = new TenantSubscription();
        subscription.setTenantId(tenantId);
        subscription.setBillingPlan(free);
        subscription.setStatus(BillingSubscriptionStatus.ACTIVE);
        subscription.setProvider(BillingProvider.MANUAL);
        subscription.setStartedAt(LocalDateTime.now());
        subscription.setCurrentPeriodStart(LocalDateTime.now());
        subscription.setLastPlanChangeAt(LocalDateTime.now());
        subscription.setCancelAtPeriodEnd(false);
        return subscriptionRepository.save(subscription);
    }

    private UUID resolveTenantId(String externalReference) {
        if (externalReference.length() < 36) {
            throw new IllegalArgumentException("Invalid Asaas externalReference");
        }
        return UUID.fromString(externalReference.substring(0, 36));
    }

    private LocalDate nextFutureMonthlyDate(LocalDate base) {
        LocalDate next = base.plusMonths(1);
        while (!next.isAfter(LocalDate.now())) {
            next = next.plusMonths(1);
        }
        return next;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value, ASAAS_DATE_TIME);
            } catch (Exception ignoredAgain) {
                try {
                    return LocalDateTime.parse(value);
                } catch (Exception ignoredToo) {
                    try {
                        return LocalDate.parse(value.substring(0, 10)).atStartOfDay();
                    } catch (Exception ignoredFinally) {
                        return null;
                    }
                }
            }
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
