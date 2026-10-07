package com.rinoimob.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.rinoimob.domain.entity.IntegrationConnection;
import com.rinoimob.domain.entity.IntegrationEvent;
import com.rinoimob.domain.enums.IntegrationConnectionStatus;
import com.rinoimob.domain.enums.IntegrationEventStatus;
import com.rinoimob.domain.enums.IntegrationProvider;
import com.rinoimob.domain.repository.IntegrationConnectionRepository;
import com.rinoimob.domain.repository.IntegrationEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "originLeadId is required");
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

    private void persist(IntegrationConnection connection, String externalId, String eventType,
                         String externalResourceId, JsonNode payload) {
        if (eventRepository.findByConnectionIdAndExternalEventId(connection.getId(), externalId).isPresent()) return;
        IntegrationEvent event = new IntegrationEvent();
        event.setTenantId(connection.getTenantId());
        event.setConnectionId(connection.getId());
        event.setProvider(connection.getProvider());
        event.setExternalEventId(externalId);
        event.setExternalResourceId(externalResourceId);
        event.setEventType(eventType);
        event.setStatus(connection.getStatus() == IntegrationConnectionStatus.ACTIVE
                ? IntegrationEventStatus.RECEIVED : IntegrationEventStatus.IGNORED);
        event.setEncryptedPayload(secretService.encrypt(payload.toString()));
        try {
            eventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException ignored) {
            return;
        }
        connection.setLastReceivedAt(LocalDateTime.now());
        connectionRepository.save(connection);
    }

    private String text(JsonNode payload, String field) {
        String value = payload.path(field).asText();
        return value == null || value.isBlank() ? null : value;
    }
}
