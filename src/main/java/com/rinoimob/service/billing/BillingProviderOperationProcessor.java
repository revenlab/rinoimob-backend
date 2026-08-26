package com.rinoimob.service.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.domain.entity.BillingProviderOperation;
import com.rinoimob.domain.enums.BillingProviderOperationStatus;
import com.rinoimob.domain.repository.BillingProviderOperationRepository;
import com.rinoimob.service.billing.payment.BillingGatewayPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BillingProviderOperationProcessor {

    private static final Logger log = LoggerFactory.getLogger(BillingProviderOperationProcessor.class);
    private static final int MAX_ATTEMPTS = 10;

    private final BillingProviderOperationRepository repository;
    private final BillingGatewayPort billingGatewayPort;
    private final ObjectMapper objectMapper;

    public BillingProviderOperationProcessor(BillingProviderOperationRepository repository,
                                             BillingGatewayPort billingGatewayPort,
                                             ObjectMapper objectMapper) {
        this.repository = repository;
        this.billingGatewayPort = billingGatewayPort;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${billing.asaas.provider-operation-interval-ms:30000}")
    @Transactional
    public void processPendingOperations() {
        List<BillingProviderOperation> operations = repository
                .findTop50ByStatusInAndNextAttemptAtBeforeOrderByCreatedAtAsc(
                        List.of(BillingProviderOperationStatus.PENDING, BillingProviderOperationStatus.FAILED),
                        LocalDateTime.now()
                );
        for (BillingProviderOperation operation : operations) {
            process(operation);
        }
    }

    private void process(BillingProviderOperation operation) {
        operation.setStatus(BillingProviderOperationStatus.PROCESSING);
        repository.save(operation);
        try {
            JsonNode payload = operation.getPayload() == null
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(operation.getPayload());
            switch (operation.getOperationType()) {
                case CANCEL_SUBSCRIPTION -> billingGatewayPort.cancelSubscription(operation.getProviderResourceId());
                case INACTIVATE_SUBSCRIPTION -> billingGatewayPort.inactivateSubscription(operation.getProviderResourceId());
                case REACTIVATE_SUBSCRIPTION -> billingGatewayPort.reactivateSubscription(
                        operation.getProviderResourceId(), LocalDate.parse(payload.path("nextDueDate").asText())
                );
                case UPDATE_SUBSCRIPTION -> billingGatewayPort.updateSubscriptionPlan(
                        operation.getProviderResourceId(),
                        new BigDecimal(payload.path("value").asText()),
                        LocalDate.parse(payload.path("nextDueDate").asText())
                );
            }
            operation.setStatus(BillingProviderOperationStatus.SUCCEEDED);
            operation.setCompletedAt(LocalDateTime.now());
            operation.setLastError(null);
        } catch (Exception exception) {
            int attempts = operation.getAttemptCount() + 1;
            operation.setAttemptCount(attempts);
            operation.setStatus(BillingProviderOperationStatus.FAILED);
            operation.setLastError(limit(exception.getMessage(), 4000));
            long delaySeconds = attempts >= MAX_ATTEMPTS ? 86400 : Math.min(3600, 30L << Math.min(attempts, 7));
            operation.setNextAttemptAt(LocalDateTime.now().plusSeconds(delaySeconds));
            log.error("Asaas provider operation {} failed for tenant {} (attempt {})",
                    operation.getOperationType(), operation.getTenantId(), attempts, exception);
        }
        repository.save(operation);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
