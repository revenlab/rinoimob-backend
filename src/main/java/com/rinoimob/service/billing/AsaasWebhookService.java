package com.rinoimob.service.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.rinoimob.domain.entity.BillingPlan;
import com.rinoimob.domain.entity.TenantSubscription;
import com.rinoimob.domain.enums.BillingPlanCode;
import com.rinoimob.domain.enums.BillingProvider;
import com.rinoimob.domain.enums.BillingSubscriptionStatus;
import com.rinoimob.domain.repository.TenantSubscriptionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class AsaasWebhookService {

    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final BillingPlanResolverService billingPlanResolverService;
    private final TenantBillingProfileService tenantBillingProfileService;

    public AsaasWebhookService(TenantSubscriptionRepository tenantSubscriptionRepository,
                               BillingPlanResolverService billingPlanResolverService,
                               TenantBillingProfileService tenantBillingProfileService) {
        this.tenantSubscriptionRepository = tenantSubscriptionRepository;
        this.billingPlanResolverService = billingPlanResolverService;
        this.tenantBillingProfileService = tenantBillingProfileService;
    }

    @Transactional
    public void handleWebhook(JsonNode payload) {
        String event = payload.path("event").asText(null);
        if (event == null || event.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing webhook event");
        }
        if (!isBillingEvent(event)) {
            return;
        }

        JsonNode checkout = payload.path("checkout");
        JsonNode payment = payload.path("payment");
        String externalReference = firstNonBlank(
                text(checkout, "externalReference"),
                text(payment, "externalReference")
        );
        UUID tenantId = resolveTenantId(externalReference);
        BillingPlan billingPlan = billingPlanResolverService.getRequiredGlobalPlan(resolvePlanCode(externalReference));
        TenantSubscription subscription = tenantSubscriptionRepository.findByTenantId(tenantId)
                .orElseGet(() -> createPendingSubscription(tenantId));

        BillingSubscriptionStatus status = mapStatus(event);
        BillingPlanCode previousPlanCode = subscription.getBillingPlan() == null ? null : subscription.getBillingPlan().getCode();
        subscription.setBillingPlan(billingPlan);
        subscription.setProvider(BillingProvider.ASAAS);
        subscription.setStatus(status);
        subscription.setProviderCheckoutId(firstNonBlank(text(checkout, "id"), subscription.getProviderCheckoutId()));
        subscription.setProviderCustomerId(firstNonBlank(text(checkout, "customer"), text(payment, "customer"), subscription.getProviderCustomerId()));
        subscription.setProviderSubscriptionId(firstNonBlank(text(payment, "subscription"), subscription.getProviderSubscriptionId()));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodStart = firstNonNull(parseDateTime(payment, "dateCreated"), subscription.getCurrentPeriodStart(), now);
        subscription.setStartedAt(firstNonNull(subscription.getStartedAt(), periodStart));
        subscription.setCurrentPeriodStart(periodStart);
        LocalDateTime dueDate = parseDateTime(payment, "dueDate");
        subscription.setCurrentPeriodEnd(firstNonNull(
                dueDate == null ? null : dueDate.plusMonths(1),
                subscription.getCurrentPeriodEnd(),
                now.plusMonths(1)
        ));
        if (status == BillingSubscriptionStatus.CANCELED) {
            subscription.setEndedAt(now);
            subscription.setCancelAtPeriodEnd(true);
            subscription.setPastDueAt(null);
        } else if (status == BillingSubscriptionStatus.PAST_DUE) {
            subscription.setPastDueAt(firstNonNull(dueDate, subscription.getPastDueAt(), now));
        } else {
            subscription.setEndedAt(null);
            subscription.setCancelAtPeriodEnd(false);
            subscription.setPastDueAt(null);
        }
        if (previousPlanCode == null || previousPlanCode != billingPlan.getCode()) {
            subscription.setLastPlanChangeAt(now);
        } else if (subscription.getLastPlanChangeAt() == null) {
            subscription.setLastPlanChangeAt(subscription.getStartedAt());
        }

        tenantSubscriptionRepository.save(subscription);
        tenantBillingProfileService.replaceProfileForPlan(
                tenantId,
                status == BillingSubscriptionStatus.CANCELED
                        ? billingPlanResolverService.getRequiredGlobalPlan(BillingPlanCode.FREE)
                        : billingPlan,
                null,
                "Synced from Asaas webhook: " + status
        );
    }

    private boolean isBillingEvent(String event) {
        return switch (event) {
            case "CHECKOUT_PAID", "CHECKOUT_CANCELED", "CHECKOUT_EXPIRED", "PAYMENT_RECEIVED", "PAYMENT_CONFIRMED", "PAYMENT_OVERDUE", "PAYMENT_REFUNDED", "PAYMENT_DELETED" -> true;
            default -> false;
        };
    }

    private BillingSubscriptionStatus mapStatus(String event) {
        return switch (event) {
            case "CHECKOUT_CANCELED", "CHECKOUT_EXPIRED", "PAYMENT_REFUNDED", "PAYMENT_DELETED" -> BillingSubscriptionStatus.CANCELED;
            case "PAYMENT_OVERDUE" -> BillingSubscriptionStatus.PAST_DUE;
            default -> BillingSubscriptionStatus.ACTIVE;
        };
    }

    private TenantSubscription createPendingSubscription(UUID tenantId) {
        TenantSubscription subscription = new TenantSubscription();
        subscription.setTenantId(tenantId);
        subscription.setBillingPlan(billingPlanResolverService.getRequiredGlobalPlan(BillingPlanCode.FREE));
        subscription.setStatus(BillingSubscriptionStatus.PENDING);
        subscription.setProvider(BillingProvider.MANUAL);
        subscription.setStartedAt(LocalDateTime.now());
        subscription.setCurrentPeriodStart(LocalDateTime.now());
        subscription.setLastPlanChangeAt(LocalDateTime.now());
        subscription.setCancelAtPeriodEnd(false);
        return subscription;
    }

    private UUID resolveTenantId(String externalReference) {
        String reference = requireExternalReference(externalReference);
        if (reference.length() < 38) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Asaas externalReference");
        }
        try {
            return UUID.fromString(reference.substring(0, 36));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid tenantId in Asaas externalReference", exception);
        }
    }

    private BillingPlanCode resolvePlanCode(String externalReference) {
        String reference = requireExternalReference(externalReference);
        if (reference.length() < 38 || reference.charAt(36) != '-') {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing planCode in Asaas externalReference");
        }
        int planEnd = reference.indexOf('-', 37);
        if (planEnd < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing planCode in Asaas externalReference");
        }
        try {
            return BillingPlanCode.valueOf(reference.substring(37, planEnd));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid planCode in Asaas externalReference", exception);
        }
    }

    private String requireExternalReference(String externalReference) {
        if (externalReference == null || externalReference.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Asaas externalReference");
        }
        return externalReference;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    private LocalDateTime parseDateTime(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (Exception ignoredToo) {
                try {
                    return LocalDate.parse(value).atStartOfDay();
                } catch (Exception ignoredAgain) {
                    return null;
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
