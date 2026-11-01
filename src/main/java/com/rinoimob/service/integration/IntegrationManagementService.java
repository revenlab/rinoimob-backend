package com.rinoimob.service.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.api.client.MetaLeadAdsClient;
import com.rinoimob.domain.dto.integration.*;
import com.rinoimob.domain.entity.*;
import com.rinoimob.domain.enums.*;
import com.rinoimob.domain.repository.*;
import com.rinoimob.service.billing.TenantPlanAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IntegrationManagementService {
    private final IntegrationConnectionRepository connectionRepository;
    private final IntegrationRouteRepository routeRepository;
    private final IntegrationEventRepository eventRepository;
    private final LeadPipelineRepository pipelineRepository;
    private final LeadPoolRepository poolRepository;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final TenantPlanAccessService planAccessService;
    private final IntegrationSecretService secretService;
    private final MetaLeadAdsClient metaClient;
    private final ObjectMapper objectMapper;

    @Value("${app.public-api-url:http://localhost:39000}")
    private String publicApiUrl;

    @Transactional(readOnly = true)
    public List<IntegrationConnectionResponse> listConnections(UUID tenantId) {
        planAccessService.requireEnabled(tenantId, BillingFeature.INTEGRATIONS);
        return connectionRepository.findAllByTenantIdOrderByCreatedAtAsc(tenantId).stream()
                .map(this::toConnectionResponse).toList();
    }

    @Transactional(readOnly = true)
    public IntegrationRoutingOptionsResponse routingOptions(UUID tenantId) {
        planAccessService.requireEnabled(tenantId, BillingFeature.INTEGRATIONS);
        List<IntegrationOptionResponse> pipelines = pipelineRepository.findActivePipelineOptions(tenantId).stream()
                .map(row -> new IntegrationOptionResponse((UUID) row[0], String.valueOf(row[1]))).toList();
        List<IntegrationOptionResponse> pools = poolRepository.findByTenantIdOrderByPriorityAsc(tenantId).stream()
                .map(pool -> new IntegrationOptionResponse(pool.getId(), pool.getName())).toList();
        List<IntegrationOptionResponse> brokers = userRepository.findByTenantIdAndActive(tenantId, true).stream()
                .map(user -> new IntegrationOptionResponse(user.getId(),
                        ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                                + (user.getLastName() == null ? "" : user.getLastName())).trim().isBlank()
                                ? user.getEmail()
                                : ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                                + (user.getLastName() == null ? "" : user.getLastName())).trim()))
                .toList();
        List<IntegrationOptionResponse> properties = propertyRepository
                .findTop100ByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId).stream()
                .map(property -> new IntegrationOptionResponse(property.getId(),
                        (property.getReferenceCode() == null ? "" : property.getReferenceCode() + " · ") + property.getTitle()))
                .toList();
        return new IntegrationRoutingOptionsResponse(pipelines, pools, brokers, properties);
    }

    @Transactional
    public IntegrationConnectionResponse createGrupoOlx(UUID tenantId, CreateGrupoOlxConnectionRequest request) {
        planAccessService.requireEnabled(tenantId, BillingFeature.INTEGRATIONS);
        IntegrationConnection connection = new IntegrationConnection();
        connection.setTenantId(tenantId);
        connection.setProvider(IntegrationProvider.GRUPO_OLX);
        connection.setName(request.name().trim());
        connection.setStatus(IntegrationConnectionStatus.ACTIVE);
        return toConnectionResponse(connectionRepository.save(connection));
    }

    @Transactional
    public MetaOAuthStartResponse startMetaOAuth(UUID tenantId, UUID userId) {
        planAccessService.requireEnabled(tenantId, BillingFeature.INTEGRATIONS);
        IntegrationConnection connection = new IntegrationConnection();
        connection.setTenantId(tenantId);
        connection.setProvider(IntegrationProvider.META_LEAD_ADS);
        connection.setName("Meta Lead Ads");
        return beginMetaOAuth(connection, userId);
    }

    @Transactional
    public MetaOAuthStartResponse reconnectMetaOAuth(UUID tenantId, UUID userId, UUID connectionId) {
        return beginMetaOAuth(requireConnection(tenantId, connectionId, IntegrationProvider.META_LEAD_ADS), userId);
    }

    private MetaOAuthStartResponse beginMetaOAuth(IntegrationConnection connection, UUID userId) {
        String nonce = UUID.randomUUID().toString();
        // Validate local configuration before creating or changing a connection.
        metaClient.authorizationUrl("configuration-check");
        try {
            secretService.validateConfiguration();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A criptografia das integrações não está configurada no servidor. Contate o administrador.");
        }
        connection.setStatus(IntegrationConnectionStatus.CONNECTING);
        connection.setLastError(null);
        connection.getConfig().put("oauthNonceHash", sha256(nonce));
        connection.getConfig().put("oauthUserId", userId.toString());
        connection = connectionRepository.save(connection);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (connection.getId() + ":" + nonce).getBytes(StandardCharsets.UTF_8)
        );
        return new MetaOAuthStartResponse(metaClient.authorizationUrl(state));
    }

    @Transactional
    public UUID completeMetaOAuth(String code, String state) {
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Meta OAuth state");
        }
        String[] parts = decoded.split(":", 2);
        if (parts.length != 2) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Meta OAuth state");
        UUID connectionId;
        try {
            connectionId = UUID.fromString(parts[0]);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Meta OAuth state");
        }
        IntegrationConnection connection = connectionRepository.findById(connectionId)
                .filter(item -> item.getProvider() == IntegrationProvider.META_LEAD_ADS)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meta connection not found"));
        String expectedHash = String.valueOf(connection.getConfig().get("oauthNonceHash"));
        if (!MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8), sha256(parts[1]).getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Meta OAuth state");
        }
        connection.getConfig().remove("oauthNonceHash");
        try {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("Authorization was not completed");
            }
            String userToken = metaClient.exchangeCode(code);
            connection.setEncryptedCredentials(secretService.encrypt(writeJson(Map.of("userAccessToken", userToken))));
            connection.setStatus(IntegrationConnectionStatus.CONNECTING);
            connection.setLastError(null);
        } catch (RuntimeException exception) {
            connection.setEncryptedCredentials(null);
            connection.setStatus(IntegrationConnectionStatus.ERROR);
            connection.setLastError("Não foi possível concluir a autorização na Meta. Reconecte para tentar novamente.");
        }
        connectionRepository.save(connection);
        return connectionId;
    }

    @Transactional(readOnly = true)
    public List<MetaAssetResponse> listMetaPages(UUID tenantId, UUID connectionId) {
        IntegrationConnection connection = requireConnection(tenantId, connectionId, IntegrationProvider.META_LEAD_ADS);
        if (!metaAuthorized(connection)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conclua a autorização na Meta antes de selecionar a página.");
        }
        String userToken = credential(connection, "userAccessToken");
        return metaClient.listPages(userToken).stream()
                .map(page -> new MetaAssetResponse(page.get("id"), page.get("name"), "PAGE"))
                .toList();
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public IntegrationConnectionResponse activateMetaPage(UUID tenantId, UUID connectionId, ActivateMetaConnectionRequest request) {
        IntegrationConnection connection = requireConnection(tenantId, connectionId, IntegrationProvider.META_LEAD_ADS);
        if (!metaAuthorized(connection)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conclua a autorização na Meta antes de selecionar a página.");
        }
        if (connection.getExternalAccountId() != null && !connection.getExternalAccountId().equals(request.pageId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reconecte a mesma página ou crie uma nova conexão para outra página.");
        }
        try {
            Map<String, String> page = metaClient.listPages(credential(connection, "userAccessToken")).stream()
                    .filter(item -> request.pageId().equals(item.get("id")))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meta page is not authorized"));
            // Verify form access before subscribing or declaring the Page active.
            metaClient.listForms(request.pageId(), page.get("accessToken"));
            metaClient.subscribePage(request.pageId(), page.get("accessToken"));
            connection.setExternalAccountId(request.pageId());
            connection.setExternalAccountName(page.get("name"));
            connection.setEncryptedCredentials(secretService.encrypt(writeJson(Map.of(
                    "userAccessToken", credential(connection, "userAccessToken"),
                    "pageAccessToken", page.get("accessToken")
            ))));
            connection.setStatus(IntegrationConnectionStatus.ACTIVE);
            connection.setLastError(null);
            resumeMetaRoutes(connection);
            return toConnectionResponse(connectionRepository.save(connection));
        } catch (RuntimeException exception) {
            connection.setStatus(IntegrationConnectionStatus.ERROR);
            connection.setLastError(exception instanceof ResponseStatusException responseException
                    ? responseException.getReason()
                    : "Não foi possível confirmar a página na Meta. Verifique as permissões e tente reconectar.");
            connectionRepository.save(connection);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, connection.getLastError());
        }
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public List<MetaAssetResponse> listMetaForms(UUID tenantId, UUID connectionId) {
        IntegrationConnection connection = requireConnection(tenantId, connectionId, IntegrationProvider.META_LEAD_ADS);
        if (connection.getExternalAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Select a Meta page first");
        }
        try {
            return metaClient.listForms(connection.getExternalAccountId(), credential(connection, "pageAccessToken")).stream()
                    .map(form -> new MetaAssetResponse(form.get("id"), form.get("name"), "FORM"))
                    .toList();
        } catch (ResponseStatusException exception) {
            connection.setStatus(IntegrationConnectionStatus.ERROR);
            connection.setLastError(exception.getReason());
            connectionRepository.save(connection);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<IntegrationRouteResponse> listRoutes(UUID tenantId, UUID connectionId) {
        requireConnection(tenantId, connectionId, null);
        return routeRepository.findAllByTenantIdAndConnectionIdOrderByExternalResourceNameAsc(tenantId, connectionId)
                .stream().map(this::toRouteResponse).toList();
    }

    @Transactional
    public IntegrationRouteResponse saveRoute(UUID tenantId, UUID connectionId, IntegrationRouteRequest request) {
        IntegrationConnection connection = requireConnection(tenantId, connectionId, null);
        validateRouteTargets(tenantId, request);
        IntegrationRoute route = routeRepository
                .findByTenantIdAndConnectionIdAndExternalResourceId(tenantId, connectionId, request.externalResourceId().trim())
                .orElseGet(IntegrationRoute::new);
        route.setTenantId(tenantId);
        route.setConnectionId(connectionId);
        route.setExternalResourceId(request.externalResourceId().trim());
        route.setExternalResourceName(blankToNull(request.externalResourceName()));
        route.setPipelineId(request.pipelineId());
        route.setPoolId(request.poolId());
        route.setAssignedTo(request.assignedTo());
        route.setDefaultPropertyId(request.defaultPropertyId());
        route.setFieldMapping(request.fieldMapping() == null ? new HashMap<>() : new HashMap<>(request.fieldMapping()));
        route.setActive(request.active() == null || request.active());
        if (connection.getProvider() == IntegrationProvider.META_LEAD_ADS && Boolean.TRUE.equals(route.getActive())) {
            route.setMetaNextSyncAt(java.time.Instant.EPOCH);
            eventRepository.requeueAfterConfigurationChange(tenantId, connectionId, route.getExternalResourceId(), java.time.LocalDateTime.now());
        }
        return toRouteResponse(routeRepository.save(route));
    }

    @Transactional
    public IntegrationConnectionResponse updateStatus(UUID tenantId, UUID connectionId, IntegrationConnectionStatus status) {
        IntegrationConnection connection = requireConnection(tenantId, connectionId, null);
        if (status != IntegrationConnectionStatus.ACTIVE && status != IntegrationConnectionStatus.PAUSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only ACTIVE or PAUSED can be selected manually");
        }
        if (connection.getProvider() == IntegrationProvider.GRUPO_OLX
                && Boolean.TRUE.equals(connection.getConfig().get("olxOAuth"))
                && (!connection.getConfig().containsKey("olxLeadConfigurationId") || connection.getEncryptedCredentials() == null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conclua a autorização e o cadastro do webhook na OLX antes de ativar.");
        }
        if (connection.getProvider() == IntegrationProvider.META_LEAD_ADS
                && (connection.getStatus() != IntegrationConnectionStatus.ACTIVE
                && connection.getStatus() != IntegrationConnectionStatus.PAUSED
                || !metaAuthorized(connection) || connection.getExternalAccountId() == null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conclua a conexão da página na Meta antes de ativar ou pausar.");
        }
        connection.setStatus(status);
        if (status == IntegrationConnectionStatus.ACTIVE) resumeMetaRoutes(connection);
        return toConnectionResponse(connectionRepository.save(connection));
    }

    @Transactional
    public void requestMetaSync(UUID tenantId, UUID connectionId) {
        IntegrationConnection connection = requireConnection(tenantId, connectionId, IntegrationProvider.META_LEAD_ADS);
        if (connection.getStatus() != IntegrationConnectionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reconecte ou ative a página antes de recuperar os leads.");
        }
        resumeMetaRoutes(connection);
    }

    private void resumeMetaRoutes(IntegrationConnection connection) {
        if (connection.getProvider() != IntegrationProvider.META_LEAD_ADS) return;
        routeRepository.requestMetaSync(connection.getTenantId(), connection.getId(), java.time.Instant.EPOCH);
        for (var route : routeRepository.findAllByTenantIdAndConnectionIdOrderByExternalResourceNameAsc(connection.getTenantId(), connection.getId())) {
            if (Boolean.TRUE.equals(route.getActive())) {
                eventRepository.requeueAfterConfigurationChange(connection.getTenantId(), connection.getId(),
                        route.getExternalResourceId(), java.time.LocalDateTime.now());
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<IntegrationEventResponse> listEvents(UUID tenantId, int page, int size) {
        planAccessService.requireEnabled(tenantId, BillingFeature.INTEGRATIONS);
        return eventRepository.findAllByTenantIdOrderByReceivedAtDesc(
                        tenantId, PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100))))
                .map(this::toEventResponse);
    }

    @Transactional
    public void retryEvent(UUID tenantId, UUID eventId) {
        IntegrationEvent event = eventRepository.findByIdAndTenantId(eventId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Integration event not found"));
        if (event.getStatus() == IntegrationEventStatus.PROCESSED || event.getStatus() == IntegrationEventStatus.IGNORED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Processed events cannot be retried");
        }
        event.setStatus(IntegrationEventStatus.RECEIVED);
        event.setAttemptCount(0);
        event.setNextAttemptAt(java.time.LocalDateTime.now());
        event.setLastError(null);
        eventRepository.save(event);
    }

    private void validateRouteTargets(UUID tenantId, IntegrationRouteRequest request) {
        if (request.poolId() != null && request.assignedTo() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose either a broker or a lead pool");
        }
        pipelineRepository.findActivePipelineId(tenantId, request.pipelineId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead pipeline not found"));
        pipelineRepository.findInitialOpenStageId(request.pipelineId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Lead pipeline has no open stage"));
        if (request.poolId() != null) poolRepository.findByIdAndTenantId(request.poolId(), tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead pool not found"));
        if (request.assignedTo() != null) userRepository.findByIdAndTenantId(request.assignedTo(), tenantId)
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Broker not found"));
        if (request.defaultPropertyId() != null) propertyRepository
                .findByIdAndTenantIdAndDeletedAtIsNull(request.defaultPropertyId(), tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found"));
    }

    private IntegrationConnection requireConnection(UUID tenantId, UUID id, IntegrationProvider provider) {
        planAccessService.requireEnabled(tenantId, BillingFeature.INTEGRATIONS);
        return connectionRepository.findByIdAndTenantId(id, tenantId)
                .filter(item -> provider == null || item.getProvider() == provider)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Integration connection not found"));
    }

    private String credential(IntegrationConnection connection, String key) {
        try {
            JsonNode node = objectMapper.readTree(secretService.decrypt(connection.getEncryptedCredentials()));
            String value = node.path(key).asText();
            if (value.isBlank()) throw new IllegalStateException("Missing integration credential " + key);
            return value;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid integration credentials", exception);
        }
    }

    private IntegrationConnectionResponse toConnectionResponse(IntegrationConnection connection) {
        String inboundUrl = connection.getProvider() == IntegrationProvider.GRUPO_OLX
                ? publicApiUrl.replaceAll("/$", "") + "/api/v1/webhooks/integrations/grupo-olx/" + connection.getInboundKey()
                : null;
        String feedUrl = connection.getProvider() == IntegrationProvider.GRUPO_OLX
                ? publicApiUrl.replaceAll("/$", "") + "/api/v1/feeds/grupo-olx/" + connection.getOutboundKey() + ".xml"
                : null;
        long pending = connection.getId() == null ? 0 : eventRepository.countByConnectionIdAndStatusIn(
                connection.getId(), List.of(IntegrationEventStatus.RECEIVED, IntegrationEventStatus.PROCESSING,
                        IntegrationEventStatus.FAILED, IntegrationEventStatus.BLOCKED_QUOTA));
        return new IntegrationConnectionResponse(connection.getId(), connection.getProvider(), connection.getName(),
                connection.getStatus(), connection.getExternalAccountId(), connection.getExternalAccountName(), inboundUrl, feedUrl,
                connection.getLastReceivedAt(), connection.getLastProcessedAt(), connection.getLastError(), pending, metaAuthorized(connection),
                Boolean.TRUE.equals(connection.getConfig().get("olxOAuth")),
                connection.getProvider() == IntegrationProvider.GRUPO_OLX && connection.getEncryptedCredentials() != null
                        && connection.getConfig().containsKey("olxLeadConfigurationId"));
    }

    private boolean metaAuthorized(IntegrationConnection connection) {
        return connection.getProvider() == IntegrationProvider.META_LEAD_ADS
                && connection.getEncryptedCredentials() != null
                && !connection.getConfig().containsKey("oauthNonceHash");
    }

    private IntegrationRouteResponse toRouteResponse(IntegrationRoute route) {
        return new IntegrationRouteResponse(route.getId(), route.getConnectionId(), route.getExternalResourceId(),
                route.getExternalResourceName(), route.getPipelineId(), route.getPoolId(), route.getAssignedTo(),
                route.getDefaultPropertyId(), route.getFieldMapping(), Boolean.TRUE.equals(route.getActive()),
                route.getMetaLastSyncAt(), route.getMetaSyncedUntil(), route.getMetaNextSyncAt(),
                route.getMetaSyncError(), route.getMetaRecoveredCount(), route.getMetaSyncFrom() != null);
    }

    private IntegrationEventResponse toEventResponse(IntegrationEvent event) {
        return new IntegrationEventResponse(event.getId(), event.getConnectionId(), event.getProvider(), event.getExternalEventId(),
                event.getEventType(), event.getStatus(), event.getAttemptCount(), event.getLastError(), event.getLeadId(),
                event.getReceivedAt(), event.getProcessedAt());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize integration data", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash integration state", exception);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
