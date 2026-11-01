package com.rinoimob.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.api.client.OlxLeadClient;
import com.rinoimob.domain.dto.integration.OlxOAuthStartResponse;
import com.rinoimob.domain.entity.IntegrationConnection;
import com.rinoimob.domain.enums.*;
import com.rinoimob.domain.repository.IntegrationConnectionRepository;
import com.rinoimob.service.billing.TenantPlanAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OlxOAuthService {
    private final IntegrationConnectionRepository connections;
    private final TenantPlanAccessService plans;
    private final IntegrationSecretService secrets;
    private final OlxLeadClient client;
    private final ObjectMapper mapper;

    @Value("${app.public-api-url:http://localhost:39000}")
    private String publicApiUrl;

    @Value("${integrations.grupo-olx.secret-key:}")
    private String legacySecret;

    @Transactional
    public OlxOAuthStartResponse start(UUID tenantId, UUID userId, UUID connectionId) {
        plans.requireEnabled(tenantId, BillingFeature.INTEGRATIONS);
        client.authorizationUrl("configuration-check");
        try {
            secrets.validateConfiguration();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Configure a criptografia das integrações no servidor.");
        }
        if (!publicApiUrl.startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Configure PUBLIC_API_URL com a URL HTTPS pública do webhook.");
        }
        IntegrationConnection connection;
        if (connectionId == null) {
            connection = new IntegrationConnection();
            connection.setTenantId(tenantId);
            connection.setProvider(IntegrationProvider.GRUPO_OLX);
            connection.setName("OLX");
            connection.setInboundKey(UUID.randomUUID().toString());
        } else {
            connection = connections.findForOAuthByIdAndTenantId(connectionId, tenantId)
                    .filter(item -> item.getProvider() == IntegrationProvider.GRUPO_OLX)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OLX connection not found"));
        }
        // Keep a working subscription active during reauthorization so deliveries are not discarded.
        if (!connection.getConfig().containsKey("olxLeadConfigurationId")) {
            connection.setStatus(IntegrationConnectionStatus.CONNECTING);
        }
        connection.setLastError(null);
        String nonce = randomToken();
        connection.getConfig().put("olxOAuth", true);
        connection.getConfig().put("olxNonceHash", hash(nonce));
        connection.getConfig().put("olxStateExpiresAt", Instant.now().plusSeconds(600).getEpochSecond());
        connection.getConfig().put("olxUserId", userId.toString());
        connection = connections.saveAndFlush(connection);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (tenantId + ":" + connection.getId() + ":" + nonce).getBytes(StandardCharsets.UTF_8));
        return new OlxOAuthStartResponse(client.authorizationUrl(state));
    }

    @Transactional
    public UUID complete(String code, String state, String error) {
        UUID tenantId;
        UUID connectionId;
        String nonce;
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8).split(":", -1);
            if (parts.length != 3) throw new IllegalArgumentException();
            tenantId = UUID.fromString(parts[0]);
            connectionId = UUID.fromString(parts[1]);
            nonce = parts[2];
        } catch (RuntimeException exception) {
            throw invalidState();
        }
        // Tenant and connection in state are untrusted until the stored nonce is checked under a lock.
        IntegrationConnection connection = connections.findForOAuthByIdAndTenantId(connectionId, tenantId)
                .filter(item -> item.getProvider() == IntegrationProvider.GRUPO_OLX)
                .orElseThrow(this::invalidState);
        String expected = String.valueOf(connection.getConfig().get("olxNonceHash"));
        Object expiry = connection.getConfig().get("olxStateExpiresAt");
        if (!(expiry instanceof Number) || ((Number) expiry).longValue() <= Instant.now().getEpochSecond()
                || !equalsSecure(expected, hash(nonce))) throw invalidState();
        connection.getConfig().remove("olxNonceHash");
        connection.getConfig().remove("olxStateExpiresAt");
        try {
            plans.requireEnabled(tenantId, BillingFeature.INTEGRATIONS);
            if (error != null || code == null || code.isBlank()) throw new IllegalStateException("Authorization denied");
            String token = client.exchangeCode(code);
            var account = client.account(token);
            if (connections.existsByTenantIdAndProviderAndExternalAccountIdAndIdNot(tenantId,
                    IntegrationProvider.GRUPO_OLX, account.email(), connectionId)) {
                throw new IllegalStateException("OLX account is already connected in this tenant");
            }
            if (connection.getExternalAccountId() != null && !connection.getExternalAccountId().equals(account.email())) {
                throw new IllegalStateException("Reconnect using the same OLX account");
            }
            Map<String, String> credentials = new HashMap<>();
            String deliveryToken = null;
            if (connection.getEncryptedCredentials() != null) {
                deliveryToken = mapper.readTree(secrets.decrypt(connection.getEncryptedCredentials())).path("webhookToken").asText(null);
            }
            if (deliveryToken == null) deliveryToken = Base64.getEncoder().encodeToString(
                    ("rinoimob:" + randomToken()).getBytes(StandardCharsets.UTF_8));
            credentials.put("accessToken", token);
            credentials.put("webhookToken", deliveryToken);
            String encryptedCredentials = secrets.encrypt(mapper.writeValueAsString(credentials));
            String configurationId = (String) connection.getConfig().get("olxLeadConfigurationId");
            String webhookUrl = publicApiUrl.replaceAll("/$", "")
                    + "/api/v1/webhooks/integrations/grupo-olx/" + connection.getInboundKey();
            String id = client.configureLeads(token, configurationId, webhookUrl, deliveryToken);
            connection.setEncryptedCredentials(encryptedCredentials);
            connection.setExternalAccountId(account.email());
            connection.setExternalAccountName(account.name());
            connection.getConfig().put("olxLeadConfigurationId", id);
            connection.getConfig().put("olxWebhookTokenHash", hash(deliveryToken));
            if (connection.getStatus() != IntegrationConnectionStatus.PAUSED) connection.setStatus(IntegrationConnectionStatus.ACTIVE);
            connection.setLastError(null);
        } catch (Exception exception) {
            // Never expose OAuth codes, tokens or provider error bodies to logs/UI.
            if (!connection.getConfig().containsKey("olxLeadConfigurationId")) connection.setStatus(IntegrationConnectionStatus.ERROR);
            connection.setLastError("Não foi possível autorizar ou cadastrar o webhook na OLX. Confira a conta e as permissões e reconecte.");
        }
        connections.save(connection);
        return connectionId;
    }

    @Transactional(readOnly = true)
    public void validateWebhook(String inboundKey, String authorization) {
        IntegrationConnection connection = connections.findByInboundKeyAndProvider(inboundKey, IntegrationProvider.GRUPO_OLX)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid OLX webhook credentials"));
        Object tokenHash = connection.getConfig().get("olxWebhookTokenHash");
        if (tokenHash != null) {
            // OLX documents a token delivered in Authorization; accept its exact value or a Basic wrapper.
            String received = authorization == null ? "" : authorization;
            if (received.startsWith("Basic ")) received = received.substring(6);
            if (!equalsSecure(tokenHash.toString(), hash(received))) throw unauthorized();
            return;
        }
        // Canal Pro uses a CRM-wide SECRET_KEY provided by Grupo ZAP, independently of OLX OAuth.
        if (Boolean.TRUE.equals(connection.getConfig().get("olxOAuth"))) throw unauthorized();
        if (legacySecret == null || legacySecret.isBlank() || authorization == null || !authorization.startsWith("Basic ")) throw unauthorized();
        try {
            String decoded = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator < 0 || !equalsSecure(legacySecret, decoded.substring(separator + 1))) throw unauthorized();
        } catch (IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash OAuth state", exception);
        }
    }

    private boolean equalsSecure(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseStatusException invalidState() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OLX OAuth state");
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid OLX webhook credentials");
    }
}
