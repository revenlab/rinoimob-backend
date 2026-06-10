package com.rinoimob.service.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.rinoimob.domain.entity.BillingPlan;
import com.rinoimob.domain.entity.TenantSubscription;
import com.rinoimob.domain.enums.BillingPlanCode;
import com.rinoimob.domain.enums.BillingProvider;
import com.rinoimob.domain.enums.BillingSubscriptionStatus;
import com.rinoimob.domain.repository.TenantSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class AbacatePayWebhookService {

    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final BillingPlanResolverService billingPlanResolverService;
    private final TenantBillingProfileService tenantBillingProfileService;

    public AbacatePayWebhookService(TenantSubscriptionRepository tenantSubscriptionRepository,
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
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Missing webhook event");
        }

        JsonNode subscriptionNode = extractSubscriptionNode(payload);
        if (subscriptionNode == null || subscriptionNode.isMissingNode() || subscriptionNode.isNull()) {
            return;
        }

        switch (event) {
            case "subscription.completed", "subscription.renewed", "subscription.trial_started" ->
                    syncSubscription(payload, subscriptionNode, mapStatus(event));
            case "subscription.cancelled" ->
                    syncSubscription(payload, subscriptionNode, BillingSubscriptionStatus.CANCELED);
            default -> {
            }
        }
    }

    private BillingSubscriptionStatus mapStatus(String event) {
        return switch (event) {
            case "subscription.trial_started" -> BillingSubscriptionStatus.TRIAL;
            default -> BillingSubscriptionStatus.ACTIVE;
        };
    }

    private void syncSubscription(JsonNode payload, JsonNode subscriptionNode, BillingSubscriptionStatus status) {
        JsonNode data = payload.path("data");
        JsonNode checkoutNode = data.path("checkout");
        JsonNode customerNode = data.path("customer");

        UUID tenantId = resolveTenantId(payload, subscriptionNode);
        BillingPlanCode planCode = resolvePlanCode(payload, subscriptionNode);
        BillingPlan billingPlan = billingPlanResolverService.getRequiredGlobalPlan(planCode);

        TenantSubscription subscription = tenantSubscriptionRepository.findByTenantId(tenantId)
                .orElseGet(() -> createPendingSubscription(tenantId));

        subscription.setBillingPlan(billingPlan);
        subscription.setProvider(BillingProvider.ABACATEPAY);
        subscription.setStatus(status);
        subscription.setProviderSubscriptionId(firstNonBlank(
                text(subscriptionNode, "id"),
                subscription.getProviderSubscriptionId()
        ));
        subscription.setProviderCustomerId(firstNonBlank(
                text(checkoutNode, "customerId"),
                text(customerNode, "id"),
                text(subscriptionNode, "customerId"),
                subscription.getProviderCustomerId()
        ));
        subscription.setProviderCheckoutId(firstNonBlank(
                text(checkoutNode, "id"),
                text(subscriptionNode, "checkoutId"),
                subscription.getProviderCheckoutId()
        ));

        LocalDateTime now = LocalDateTime.now();
        subscription.setStartedAt(firstNonNull(
                parseDateTime(subscriptionNode, "startedAt"),
                parseDateTime(subscriptionNode, "createdAt"),
                subscription.getStartedAt(),
                now
        ));
        subscription.setCurrentPeriodStart(firstNonNull(
                parseDateTime(subscriptionNode, "currentPeriodStart"),
                subscription.getCurrentPeriodStart(),
                now
        ));
        subscription.setCurrentPeriodEnd(firstNonNull(
                parseDateTime(subscriptionNode, "currentPeriodEnd"),
                status == BillingSubscriptionStatus.CANCELED ? now : subscription.getCurrentPeriodEnd(),
                status == BillingSubscriptionStatus.CANCELED ? now : now.plusMonths(1)
        ));
        if (status == BillingSubscriptionStatus.CANCELED) {
            subscription.setEndedAt(firstNonNull(parseDateTime(subscriptionNode, "endedAt"), now));
            subscription.setCancelAtPeriodEnd(true);
        } else {
            subscription.setEndedAt(parseDateTime(subscriptionNode, "endedAt"));
            subscription.setCancelAtPeriodEnd(false);
        }

        tenantSubscriptionRepository.save(subscription);
        BillingPlan profilePlan = status == BillingSubscriptionStatus.CANCELED
                ? billingPlanResolverService.getRequiredGlobalPlan(BillingPlanCode.FREE)
                : billingPlan;

        tenantBillingProfileService.replaceProfileForPlan(
                tenantId,
                profilePlan,
                null,
                "Synced from AbacatePay webhook: " + status
        );
    }

    private TenantSubscription createPendingSubscription(UUID tenantId) {
        TenantSubscription subscription = new TenantSubscription();
        subscription.setTenantId(tenantId);
        subscription.setBillingPlan(billingPlanResolverService.getRequiredGlobalPlan(BillingPlanCode.FREE));
        subscription.setStatus(BillingSubscriptionStatus.PENDING);
        subscription.setProvider(BillingProvider.MANUAL);
        subscription.setStartedAt(LocalDateTime.now());
        subscription.setCurrentPeriodStart(LocalDateTime.now());
        subscription.setCancelAtPeriodEnd(false);
        return subscription;
    }

    private UUID resolveTenantId(JsonNode payload, JsonNode subscriptionNode) {
        JsonNode data = payload.path("data");
        JsonNode checkout = data.path("checkout");
        String tenantId = firstNonBlank(
                text(subscriptionNode, "externalId"),
                text(checkout, "externalId"),
                data.path("externalId").asText(null),
                subscriptionNode.path("metadata").path("tenantId").asText(null),
                checkout.path("metadata").path("tenantId").asText(null),
                data.path("metadata").path("tenantId").asText(null),
                data.path("payment").path("externalId").asText(null)
        );
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Missing tenantId in webhook payload");
        }
        return UUID.fromString(tenantId);
    }

    private BillingPlanCode resolvePlanCode(JsonNode payload, JsonNode subscriptionNode) {
        JsonNode data = payload.path("data");
        JsonNode checkout = data.path("checkout");
        String raw = firstNonBlank(
                subscriptionNode.path("metadata").path("planCode").asText(null),
                subscriptionNode.path("metadata").path("plan").asText(null),
                checkout.path("metadata").path("planCode").asText(null),
                checkout.path("metadata").path("plan").asText(null),
                data.path("metadata").path("planCode").asText(null),
                data.path("metadata").path("plan").asText(null)
        );
        if (raw == null || raw.isBlank()) {
            return BillingPlanCode.FREE;
        }
        return BillingPlanCode.valueOf(raw);
    }

    private JsonNode extractSubscriptionNode(JsonNode payload) {
        JsonNode data = payload.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return payload;
        }
        JsonNode subscription = data.path("subscription");
        if (!subscription.isMissingNode() && !subscription.isNull()) {
            return subscription;
        }
        return data;
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
                return null;
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
