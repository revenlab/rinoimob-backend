package com.rinoimob.service.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.api.client.MetaLeadAdsClient;
import com.rinoimob.domain.dto.CreateLeadRequest;
import com.rinoimob.domain.dto.LeadResponse;
import com.rinoimob.domain.entity.*;
import com.rinoimob.domain.enums.BillingFeature;
import com.rinoimob.domain.enums.IntegrationConnectionStatus;
import com.rinoimob.domain.enums.IntegrationEventStatus;
import com.rinoimob.domain.enums.IntegrationProvider;
import com.rinoimob.domain.repository.*;
import com.rinoimob.service.billing.TenantPlanAccessService;
import com.rinoimob.service.crm.LeadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationEventProcessor {
    private final IntegrationEventRepository eventRepository;
    private final IntegrationConnectionRepository connectionRepository;
    private final IntegrationRouteRepository routeRepository;
    private final LeadAcquisitionRepository acquisitionRepository;
    private final PropertyRepository propertyRepository;
    private final LeadService leadService;
    private final TenantPlanAccessService planAccessService;
    private final IntegrationSecretService secretService;
    private final MetaLeadAdsClient metaClient;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(fixedDelayString = "${integrations.processing-interval-ms:2000}")
    public void processPending() {
        List<IntegrationEvent> events = eventRepository
                .findTop50ByStatusInAndNextAttemptAtBeforeOrderByReceivedAtAsc(
                        List.of(IntegrationEventStatus.RECEIVED, IntegrationEventStatus.FAILED), LocalDateTime.now());
        events.forEach(this::processOneSafely);
    }

    @Scheduled(fixedDelayString = "${integrations.quota-recheck-interval-ms:3600000}")
    @Transactional
    public void requeueQuotaBlocked() {
        eventRepository.findTop50ByStatusInAndNextAttemptAtBeforeOrderByReceivedAtAsc(
                        List.of(IntegrationEventStatus.BLOCKED_QUOTA), LocalDateTime.now())
                .forEach(event -> {
                    event.setStatus(IntegrationEventStatus.RECEIVED);
                    event.setNextAttemptAt(LocalDateTime.now());
                    eventRepository.save(event);
                });
    }

    @Scheduled(cron = "${integrations.retention-cron:0 30 3 * * *}")
    @Transactional
    public void purgeExpiredEvents() {
        eventRepository.deleteByReceivedAtBefore(LocalDateTime.now().minusDays(90));
    }

    private void processOneSafely(IntegrationEvent event) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> process(event));
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == HttpStatus.CONFLICT.value()
                    && exception.getReason() != null && exception.getReason().contains("limite de leads")) {
                blockForQuota(event, exception.getReason());
            } else {
                fail(event, exception.getReason() == null ? exception.getMessage() : exception.getReason());
            }
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden exception) {
            expireConnection(event, "Provider authorization expired");
            fail(event, "Provider authorization expired");
        } catch (Exception exception) {
            log.warn("Integration event failed id={} provider={}", event.getId(), event.getProvider(), exception);
            fail(event, safeMessage(exception));
        }
    }

    private void process(IntegrationEvent event) {
        IntegrationEvent current = eventRepository.findByIdForUpdate(event.getId()).orElse(null);
        if (current == null || current.getLeadId() != null || current.getStatus() == IntegrationEventStatus.PROCESSED) return;
        IntegrationConnection connection = connectionRepository.findById(current.getConnectionId())
                .orElseThrow(() -> new IllegalStateException("Integration connection not found"));
        if (connection.getStatus() != IntegrationConnectionStatus.ACTIVE) {
            current.setStatus(IntegrationEventStatus.IGNORED);
            current.setLastError("Connection is not active");
            eventRepository.save(current);
            return;
        }
        if (!planAccessService.isEnabled(current.getTenantId(), BillingFeature.INTEGRATIONS)) {
            current.setStatus(IntegrationEventStatus.BLOCKED_QUOTA);
            current.setLastError("Integrações não estão habilitadas no plano atual");
            current.setNextAttemptAt(LocalDateTime.now().plusHours(1));
            eventRepository.save(current);
            return;
        }
        current.setStatus(IntegrationEventStatus.PROCESSING);
        current.setAttemptCount(current.getAttemptCount() + 1);
        eventRepository.save(current);

        NormalizedLead normalized;
        try {
            normalized = current.getProvider() == IntegrationProvider.META_LEAD_ADS
                    ? normalizeMeta(connection, current) : normalizeGrupoOlx(current);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not normalize integration event", exception);
        }
        IntegrationRoute route = routeRepository
                .findByTenantIdAndConnectionIdAndExternalResourceIdAndActiveTrue(
                        current.getTenantId(), current.getConnectionId(), normalized.routeKey())
                .orElseThrow(() -> new IllegalStateException("No active route configured for " + normalized.routeKey()));

        UUID propertyId = route.getDefaultPropertyId();
        String propertyMatchStatus = propertyId == null ? "NOT_APPLICABLE" : "MATCHED";
        if (current.getProvider() == IntegrationProvider.GRUPO_OLX && normalized.externalListingId() != null) {
            Optional<Property> property = propertyRepository.findByTenantIdAndReferenceCodeIgnoreCaseAndDeletedAtIsNull(
                    current.getTenantId(), normalized.externalListingId());
            if (property.isPresent()) {
                propertyId = property.get().getId();
                propertyMatchStatus = "MATCHED";
            } else {
                propertyMatchStatus = "NOT_FOUND";
            }
        }

        CreateLeadRequest request = new CreateLeadRequest(normalized.name(), normalized.email(), normalized.phone(),
                normalized.message(), propertyId, current.getProvider().name(), null);
        LeadResponse lead = leadService.createFromIntegration(current.getTenantId(), request, route.getPipelineId(),
                route.getPoolId(), route.getAssignedTo());

        LeadAcquisition acquisition = new LeadAcquisition();
        acquisition.setTenantId(current.getTenantId());
        acquisition.setLeadId(lead.id());
        acquisition.setConnectionId(current.getConnectionId());
        acquisition.setProvider(current.getProvider());
        acquisition.setExternalLeadId(current.getExternalEventId());
        acquisition.setExternalFormId(current.getProvider() == IntegrationProvider.META_LEAD_ADS ? normalized.routeKey() : null);
        acquisition.setExternalListingId(normalized.externalListingId());
        acquisition.setCampaignName(normalized.campaignName());
        acquisition.setAdName(normalized.adName());
        acquisition.setPropertyMatchStatus(propertyMatchStatus);
        acquisition.setProviderCreatedAt(normalized.providerCreatedAt());
        acquisition.setMetadata(normalized.metadata());
        acquisitionRepository.save(acquisition);

        current.setLeadId(lead.id());
        current.setStatus(IntegrationEventStatus.PROCESSED);
        current.setProcessedAt(LocalDateTime.now());
        current.setLastError(null);
        eventRepository.save(current);
        connection.setLastProcessedAt(LocalDateTime.now());
        connection.setLastError(null);
        connectionRepository.save(connection);
    }

    private NormalizedLead normalizeMeta(IntegrationConnection connection, IntegrationEvent event) throws Exception {
        JsonNode webhook = objectMapper.readTree(secretService.decrypt(event.getEncryptedPayload()));
        String leadgenId = webhook.path("leadgen_id").asText(event.getExternalEventId());
        JsonNode lead = metaClient.getLead(leadgenId, credential(connection, "pageAccessToken"));
        Map<String, String> fields = new LinkedHashMap<>();
        lead.path("field_data").forEach(field -> {
            String value = joinValues(field.path("values"));
            fields.put(field.path("name").asText(), value);
        });
        IntegrationRoute route = routeRepository
                .findByTenantIdAndConnectionIdAndExternalResourceIdAndActiveTrue(
                        event.getTenantId(), event.getConnectionId(), lead.path("form_id").asText(event.getExternalResourceId()))
                .orElse(null);
        Map<String, Object> mapping = route == null ? Map.of() : route.getFieldMapping();
        String name = mapped(fields, mapping, "name", "full_name", "name");
        if (blank(name)) {
            String first = mapped(fields, mapping, "firstName", "first_name");
            String last = mapped(fields, mapping, "lastName", "last_name");
            name = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        }
        if (blank(name)) throw new IllegalStateException("Meta form did not provide a mapped name");
        return new NormalizedLead(
                name,
                mapped(fields, mapping, "email", "email"),
                mapped(fields, mapping, "phone", "phone_number", "phone"),
                mapped(fields, mapping, "message", "message"),
                lead.path("form_id").asText(event.getExternalResourceId()),
                null,
                text(lead, "campaign_name"),
                text(lead, "ad_name"),
                parseDate(text(lead, "created_time")),
                new LinkedHashMap<>(fields)
        );
    }

    private NormalizedLead normalizeGrupoOlx(IntegrationEvent event) throws Exception {
        JsonNode payload = objectMapper.readTree(secretService.decrypt(event.getEncryptedPayload()));
        String name = text(payload, "name");
        if (blank(name)) throw new IllegalStateException("Grupo OLX lead name is required");
        String phone = text(payload, "phoneNumber");
        if (blank(phone)) {
            String ddd = Optional.ofNullable(text(payload, "ddd")).orElse("");
            phone = ddd + Optional.ofNullable(text(payload, "phone")).orElse("");
        }
        String listing = text(payload, "clientListingId");
        if (listing == null) listing = text(payload, "originListingId");
        if (listing == null) listing = text(payload, "adId");
        Map<String, Object> metadata = objectMapper.convertValue(payload, new TypeReference<>() {});
        return new NormalizedLead(name, text(payload, "email"), blank(phone) ? null : phone,
                text(payload, "message"), "*", listing, null, null,
                parseDate(Optional.ofNullable(text(payload, "timestamp")).orElse(text(payload, "createdAt"))), metadata);
    }

    @Transactional
    protected void blockForQuota(IntegrationEvent event, String message) {
        eventRepository.findById(event.getId()).ifPresent(current -> {
            current.setStatus(IntegrationEventStatus.BLOCKED_QUOTA);
            current.setLastError(message);
            current.setNextAttemptAt(LocalDateTime.now().plusHours(1));
            eventRepository.save(current);
        });
    }

    @Transactional
    protected void fail(IntegrationEvent event, String message) {
        eventRepository.findById(event.getId()).ifPresent(current -> {
            current.setStatus(IntegrationEventStatus.FAILED);
            current.setLastError(message);
            int attempts = (current.getAttemptCount() == null ? 0 : current.getAttemptCount()) + 1;
            current.setAttemptCount(attempts);
            current.setNextAttemptAt(attempts >= 8
                    ? LocalDateTime.now().plusYears(100)
                    : LocalDateTime.now().plusMinutes(Math.min(60, 1L << Math.min(attempts, 6))));
            eventRepository.save(current);
            connectionRepository.findById(current.getConnectionId()).ifPresent(connection -> {
                connection.setLastError(message);
                connectionRepository.save(connection);
            });
        });
    }

    private void expireConnection(IntegrationEvent event, String message) {
        connectionRepository.findById(event.getConnectionId()).ifPresent(connection -> {
            connection.setStatus(IntegrationConnectionStatus.EXPIRED);
            connection.setLastError(message);
            connectionRepository.save(connection);
        });
    }

    private String credential(IntegrationConnection connection, String key) throws Exception {
        JsonNode credentials = objectMapper.readTree(secretService.decrypt(connection.getEncryptedCredentials()));
        String value = credentials.path(key).asText();
        if (value.isBlank()) throw new IllegalStateException("Missing integration credential " + key);
        return value;
    }

    private String mapped(Map<String, String> fields, Map<String, Object> mapping, String target, String... defaults) {
        Object configured = mapping.get(target);
        if (configured != null && fields.containsKey(configured.toString())) return fields.get(configured.toString());
        for (String fallback : defaults) if (fields.containsKey(fallback)) return fields.get(fallback);
        return null;
    }

    private String joinValues(JsonNode values) {
        List<String> items = new ArrayList<>();
        values.forEach(value -> items.add(value.asText()));
        return String.join(", ", items);
    }

    private LocalDateTime parseDate(String value) {
        if (blank(value)) return null;
        try { return OffsetDateTime.parse(value).toLocalDateTime(); } catch (Exception ignored) {}
        try { return Instant.ofEpochSecond(Long.parseLong(value)).atZone(ZoneOffset.UTC).toLocalDateTime(); } catch (Exception ignored) {}
        return null;
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText();
        return blank(value) ? null : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message.substring(0, Math.min(1000, message.length()));
    }

    private record NormalizedLead(String name, String email, String phone, String message, String routeKey,
                                  String externalListingId, String campaignName, String adName,
                                  LocalDateTime providerCreatedAt, Map<String, Object> metadata) {}
}
