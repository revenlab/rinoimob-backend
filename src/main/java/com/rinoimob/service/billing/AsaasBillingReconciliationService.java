package com.rinoimob.service.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rinoimob.domain.entity.TenantBillingProfile;
import com.rinoimob.domain.entity.TenantSubscription;
import com.rinoimob.domain.repository.TenantBillingProfileRepository;
import com.rinoimob.domain.repository.TenantSubscriptionRepository;
import com.rinoimob.service.billing.payment.BillingGatewayPort;
import com.rinoimob.service.billing.payment.dto.BillingProviderPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class AsaasBillingReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(AsaasBillingReconciliationService.class);
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES_PER_CUSTOMER = 10;
    private static final DateTimeFormatter ASAAS_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TenantBillingProfileRepository profileRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final BillingGatewayPort billingGatewayPort;
    private final AsaasWebhookInboxService inboxService;
    private final ObjectMapper objectMapper;

    public AsaasBillingReconciliationService(TenantBillingProfileRepository profileRepository,
                                              TenantSubscriptionRepository subscriptionRepository,
                                              BillingGatewayPort billingGatewayPort,
                                              AsaasWebhookInboxService inboxService,
                                              ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.billingGatewayPort = billingGatewayPort;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${billing.asaas.reconciliation-interval-ms:900000}")
    public void reconcile() {
        for (TenantBillingProfile profile : profileRepository.findAllByProviderCustomerIdIsNotNull()) {
            try {
                reconcilePayments(profile);
                reconcileSubscription(profile);
            } catch (Exception exception) {
                log.error("Could not reconcile Asaas billing for tenant {}", profile.getTenantId(), exception);
            }
        }
    }

    private void reconcilePayments(TenantBillingProfile profile) {
        int offset = 0;
        for (int page = 0; page < MAX_PAGES_PER_CUSTOMER; page++) {
            BillingProviderPage result = billingGatewayPort.listCustomerPayments(
                    profile.getProviderCustomerId(), offset, PAGE_SIZE
            );
            for (Map<String, Object> payment : result.data()) {
                String paymentId = string(payment.get("id"));
                String event = mapPaymentEvent(string(payment.get("status")));
                if (paymentId == null || event == null) {
                    continue;
                }
                ObjectNode payload = basePayload("reconcile-" + paymentId + "-" + event + "-"
                        + string(payment.get("dueDate")), event);
                payload.set("payment", objectMapper.valueToTree(payment));
                inboxService.receive(payload);
            }
            if (!result.hasMore()) {
                break;
            }
            offset += PAGE_SIZE;
        }
    }

    private void reconcileSubscription(TenantBillingProfile profile) {
        TenantSubscription subscription = subscriptionRepository.findByTenantId(profile.getTenantId()).orElse(null);
        if (subscription == null || subscription.getProviderSubscriptionId() == null) {
            return;
        }
        Map<String, Object> providerSubscription = billingGatewayPort.getSubscription(subscription.getProviderSubscriptionId());
        String status = string(providerSubscription.get("status"));
        String event = "INACTIVE".equals(status) ? "SUBSCRIPTION_INACTIVATED" : "SUBSCRIPTION_UPDATED";
        ObjectNode payload = basePayload(
                "reconcile-" + subscription.getProviderSubscriptionId() + "-" + status
                        + "-" + string(providerSubscription.get("nextDueDate")),
                event
        );
        payload.set("subscription", objectMapper.valueToTree(providerSubscription));
        inboxService.receive(payload);
    }

    private ObjectNode basePayload(String eventId, String event) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", eventId);
        payload.put("event", event);
        payload.put("dateCreated", LocalDateTime.now().format(ASAAS_DATE_TIME));
        return payload;
    }

    private String mapPaymentEvent(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "PENDING" -> "PAYMENT_CREATED";
            case "CONFIRMED" -> "PAYMENT_CONFIRMED";
            case "RECEIVED", "RECEIVED_IN_CASH" -> "PAYMENT_RECEIVED";
            case "OVERDUE" -> "PAYMENT_OVERDUE";
            case "REFUNDED", "PARTIALLY_REFUNDED" -> "PAYMENT_REFUNDED";
            case "CHARGEBACK_REQUESTED", "CHARGEBACK_DISPUTE" -> "PAYMENT_CHARGEBACK_REQUESTED";
            default -> null;
        };
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
