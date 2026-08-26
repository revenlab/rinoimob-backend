package com.rinoimob.service.billing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.domain.repository.AsaasWebhookEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

@Service
public class AsaasWebhookInboxService {

    private static final DateTimeFormatter ASAAS_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AsaasWebhookEventRepository repository;
    private final ObjectMapper objectMapper;

    public AsaasWebhookInboxService(AsaasWebhookEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public boolean receive(JsonNode payload) {
        String payloadJson = write(payload);
        String eventId = text(payload, "id");
        if (eventId == null || eventId.isBlank()) {
            eventId = "missing-id-" + sha256(payloadJson);
        }
        JsonNode resource = resolveResource(payload);
        LocalDateTime receivedAt = LocalDateTime.now();
        return repository.insertIfAbsent(
                java.util.UUID.randomUUID(), eventId,
                firstNonBlank(text(payload, "event"), "UNKNOWN"),
                text(payload.path("account"), "id"), resolveResourceType(payload), text(resource, "id"),
                payloadJson, parseDateTime(text(payload, "dateCreated")), receivedAt
        ) == 1;
    }

    private JsonNode resolveResource(JsonNode payload) {
        if (payload.hasNonNull("payment")) {
            return payload.path("payment");
        }
        if (payload.hasNonNull("checkout")) {
            return payload.path("checkout");
        }
        return payload.path("subscription");
    }

    private String resolveResourceType(JsonNode payload) {
        if (payload.hasNonNull("payment")) {
            return "PAYMENT";
        }
        if (payload.hasNonNull("checkout")) {
            return "CHECKOUT";
        }
        if (payload.hasNonNull("subscription")) {
            return "SUBSCRIPTION";
        }
        return "UNKNOWN";
    }

    private String write(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid Asaas webhook payload", exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not fingerprint Asaas webhook payload", exception);
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, ASAAS_DATE_TIME);
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
