package com.rinoimob.service.billing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.domain.enums.BillingProviderOperationType;
import com.rinoimob.domain.repository.BillingProviderOperationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;

@Service
public class BillingProviderOperationService {

    private final BillingProviderOperationRepository repository;
    private final ObjectMapper objectMapper;

    public BillingProviderOperationService(BillingProviderOperationRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueue(UUID tenantId, BillingProviderOperationType type, String resourceId,
                        String idempotencyKey, Map<String, Object> payload) {
        if (resourceId == null || resourceId.isBlank() || repository.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }
        repository.insertIfAbsent(
                UUID.randomUUID(), tenantId, type.name(), resourceId, idempotencyKey,
                writePayload(payload), LocalDateTime.now()
        );
    }

    private String writePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid billing provider operation payload", exception);
        }
    }
}
