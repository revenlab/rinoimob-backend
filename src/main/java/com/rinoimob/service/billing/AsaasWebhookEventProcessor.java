package com.rinoimob.service.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.domain.entity.AsaasWebhookEvent;
import com.rinoimob.domain.enums.AsaasWebhookEventStatus;
import com.rinoimob.domain.repository.AsaasWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AsaasWebhookEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(AsaasWebhookEventProcessor.class);

    private final AsaasWebhookEventRepository repository;
    private final AsaasWebhookService webhookService;
    private final ObjectMapper objectMapper;

    public AsaasWebhookEventProcessor(AsaasWebhookEventRepository repository,
                                      AsaasWebhookService webhookService,
                                      ObjectMapper objectMapper) {
        this.repository = repository;
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${billing.asaas.webhook-processing-interval-ms:2000}")
    @Transactional
    public void processPendingEvents() {
        List<AsaasWebhookEvent> events = repository
                .findTop50ByStatusInAndNextAttemptAtBeforeOrderByReceivedAtAsc(
                        List.of(AsaasWebhookEventStatus.RECEIVED, AsaasWebhookEventStatus.FAILED),
                        LocalDateTime.now()
                );
        for (AsaasWebhookEvent event : events) {
            process(event);
        }
    }

    private void process(AsaasWebhookEvent event) {
        event.setStatus(AsaasWebhookEventStatus.PROCESSING);
        repository.save(event);
        try {
            JsonNode payload = objectMapper.readTree(event.getPayloadJson());
            if (!webhookService.supports(event.getEventType())) {
                event.setStatus(AsaasWebhookEventStatus.IGNORED);
            } else {
                UUID tenantId = webhookService.handleWebhook(payload);
                event.setTenantId(tenantId);
                event.setStatus(AsaasWebhookEventStatus.PROCESSED);
            }
            event.setProcessedAt(LocalDateTime.now());
            event.setLastError(null);
        } catch (Exception exception) {
            int attempts = event.getAttemptCount() + 1;
            event.setAttemptCount(attempts);
            event.setStatus(AsaasWebhookEventStatus.FAILED);
            event.setLastError(limit(exception.getMessage(), 4000));
            long delaySeconds = attempts >= 10 ? 86400 : Math.min(3600, 30L << Math.min(attempts, 7));
            event.setNextAttemptAt(LocalDateTime.now().plusSeconds(delaySeconds));
            log.error("Failed to process Asaas event {} ({}) attempt {}",
                    event.getProviderEventId(), event.getEventType(), attempts, exception);
        }
        repository.save(event);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
