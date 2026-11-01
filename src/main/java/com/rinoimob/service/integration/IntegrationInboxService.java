package com.rinoimob.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rinoimob.api.client.MetaLeadAdsClient.LeadReference;
import com.rinoimob.domain.entity.IntegrationConnection;
import com.rinoimob.domain.enums.IntegrationConnectionStatus;
import com.rinoimob.domain.enums.IntegrationProvider;
import com.rinoimob.domain.repository.IntegrationConnectionRepository;
import com.rinoimob.domain.repository.IntegrationEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IntegrationInboxService {
    private final IntegrationConnectionRepository connectionRepository;
    private final IntegrationEventRepository eventRepository;
    private final IntegrationSecretService secretService;

    @Transactional
    public void receiveGrupoOlx(String inboundKey, JsonNode payload) {
        IntegrationConnection connection = connectionRepository
                .findByInboundKeyAndProvider(inboundKey, IntegrationProvider.GRUPO_OLX)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Integration connection not found"));
        String externalId = text(payload, "originLeadId");
        if (externalId == null) externalId = text(payload, "externalId");
        if (externalId == null) {
            // externalId is optional in OLX's schema. Use stable event fields, never just the listing ID.
            if (text(payload, "createdAt") == null || text(payload, "listId") == null || text(payload, "name") == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lead ID or OLX event identity fields are required");
            }
            var identity = JsonNodeFactory.instance.arrayNode();
            for (String field : List.of("source", "listId", "createdAt", "name", "email", "phone", "message")) {
                identity.add(text(payload, field));
            }
            try {
                externalId = "olx-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(identity.toString().getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("Could not identify OLX lead", exception);
            }
        }
        persist(connection, externalId, "LEAD", "*", payload);
    }

    @Transactional
    public void receiveMeta(JsonNode payload) {
        if (!"page".equals(payload.path("object").asText())) return;
        for (JsonNode entry : payload.path("entry")) {
            String pageId = entry.path("id").asText();
            for (IntegrationConnection connection : connectionRepository
                    .findAllByProviderAndExternalAccountId(IntegrationProvider.META_LEAD_ADS, pageId)) {
                for (JsonNode change : entry.path("changes")) {
                    if (!"leadgen".equals(change.path("field").asText())) continue;
                    JsonNode value = change.path("value");
                    String leadgenId = value.path("leadgen_id").asText();
                    String formId = value.path("form_id").asText();
                    if (!leadgenId.isBlank()) persist(connection, leadgenId, "LEADGEN", formId, value);
                }
            }
        }
    }

    @Transactional
    public int recoverMetaLead(UUID tenantId, UUID connectionId,
                              LeadReference lead) {
        IntegrationConnection connection = connectionRepository.findByIdAndTenantId(connectionId, tenantId)
                .filter(item -> item.getProvider() == IntegrationProvider.META_LEAD_ADS
                        && item.getStatus() == IntegrationConnectionStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Meta connection is not active"));
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("leadgen_id", lead.id());
        payload.put("form_id", lead.formId());
        payload.put("page_id", connection.getExternalAccountId());
        payload.put("created_time", lead.createdAt().getEpochSecond());
        payload.put("recovered", true);
        return persist(connection, lead.id(), "LEADGEN_RECOVERED", lead.formId(), payload);
    }

    private int persist(IntegrationConnection connection, String externalId, String eventType,
                         String externalResourceId, JsonNode payload) {
        LocalDateTime now = LocalDateTime.now();
        String status = connection.getStatus() == IntegrationConnectionStatus.ACTIVE ? "RECEIVED" : "IGNORED";
        int inserted = eventRepository.insertIfAbsent(UUID.randomUUID(), connection.getTenantId(),
                connection.getId(), connection.getProvider().name(), externalId, externalResourceId, eventType,
                status, secretService.encrypt(payload.toString()), now);
        if (inserted > 0) {
            connection.setLastReceivedAt(now);
            connectionRepository.save(connection);
        }
        return inserted;
    }

    private String text(JsonNode payload, String field) {
        String value = payload.path(field).asText();
        return value == null || value.isBlank() ? null : value;
    }
}
