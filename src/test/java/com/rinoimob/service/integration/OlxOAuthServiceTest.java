package com.rinoimob.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.api.client.OlxLeadClient;
import com.rinoimob.domain.entity.IntegrationConnection;
import com.rinoimob.domain.enums.*;
import com.rinoimob.domain.repository.IntegrationConnectionRepository;
import com.rinoimob.service.billing.TenantPlanAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OlxOAuthServiceTest {
    private final IntegrationConnectionRepository repository = mock(IntegrationConnectionRepository.class);
    private final OlxLeadClient client = mock(OlxLeadClient.class);
    private final TenantPlanAccessService plans = mock(TenantPlanAccessService.class);
    private final IntegrationSecretService secrets = new IntegrationSecretService("stable-test-encryption-key-longer-than-32-chars");
    private final ObjectMapper mapper = new ObjectMapper();
    private final OlxOAuthService service = new OlxOAuthService(repository, plans, secrets, client, mapper);
    private final UUID tenant = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private IntegrationConnection connection;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "publicApiUrl", "https://api.example.com/");
        ReflectionTestUtils.setField(service, "legacySecret", "legacy-secret");
        when(client.authorizationUrl(anyString())).thenAnswer(call -> call.getArgument(0));
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            connection = call.getArgument(0);
            if (connection.getId() == null) connection.setId(UUID.randomUUID());
            when(repository.findForOAuthByIdAndTenantId(connection.getId(), tenant)).thenReturn(Optional.of(connection));
            when(repository.findByInboundKeyAndProvider(connection.getInboundKey(), IntegrationProvider.GRUPO_OLX)).thenReturn(Optional.of(connection));
            return connection;
        });
        when(client.exchangeCode("code")).thenReturn("private-access-token");
        when(client.account("private-access-token")).thenReturn(new OlxLeadClient.Account("advertiser@example.com", "Anunciante"));
        when(client.configureLeads(anyString(), nullable(String.class), anyString(), anyString())).thenReturn("configuration-id");
    }

    @Test
    void shouldRegisterPerAdvertiserWebhookAndEncryptTokens() throws Exception {
        String state = service.start(tenant, user, null).authorizationUrl();
        assertThat(connection.getStatus()).isEqualTo(IntegrationConnectionStatus.CONNECTING);
        assertThat(service.complete("code", state, null)).isEqualTo(connection.getId());
        assertThat(connection.getStatus()).isEqualTo(IntegrationConnectionStatus.ACTIVE);
        assertThat(connection.getConfig()).containsEntry("olxLeadConfigurationId", "configuration-id").doesNotContainKey("olxNonceHash");
        assertThat(connection.getEncryptedCredentials()).doesNotContain("private-access-token");
        var credentials = mapper.readTree(secrets.decrypt(connection.getEncryptedCredentials()));
        assertThat(credentials.path("accessToken").asText()).isEqualTo("private-access-token");
        String deliveryToken = credentials.path("webhookToken").asText();
        verify(client).configureLeads("private-access-token", null,
                "https://api.example.com/api/v1/webhooks/integrations/grupo-olx/" + connection.getInboundKey(), deliveryToken);
        service.validateWebhook(connection.getInboundKey(), deliveryToken);
        service.validateWebhook(connection.getInboundKey(), "Basic " + deliveryToken);
        assertThatThrownBy(() -> service.validateWebhook(connection.getInboundKey(), basic("legacy-secret")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.validateWebhook(connection.getInboundKey(), null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldRejectReplayExpiredAndTamperedStateBeforeTokenExchange() {
        String state = service.start(tenant, user, null).authorizationUrl();
        String decoded = new String(Base64.getUrlDecoder().decode(state), java.nio.charset.StandardCharsets.UTF_8);
        String tampered = Base64.getUrlEncoder().encodeToString((decoded + "tampered").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.complete("code", tampered, null)).isInstanceOf(ResponseStatusException.class);
        connection.getConfig().put("olxStateExpiresAt", Instant.now().minusSeconds(1).getEpochSecond());
        assertThatThrownBy(() -> service.complete("code", state, null)).isInstanceOf(ResponseStatusException.class);
        verify(client, never()).exchangeCode(any());
        connection.getConfig().put("olxStateExpiresAt", Instant.now().plusSeconds(60).getEpochSecond());
        service.complete("code", state, null);
        assertThatThrownBy(() -> service.complete("code", state, null)).isInstanceOf(ResponseStatusException.class);
        verify(client, times(1)).exchangeCode("code");
    }

    @Test
    void shouldConsumeDeniedAuthorizationWithoutActivatingConnection() {
        String state = service.start(tenant, user, null).authorizationUrl();
        service.complete(null, state, "access_denied");
        assertThat(connection.getStatus()).isEqualTo(IntegrationConnectionStatus.ERROR);
        assertThat(connection.getConfig()).doesNotContainKey("olxNonceHash");
        verify(client, never()).exchangeCode(any());
    }

    @Test
    void shouldNotActivateWhenRegistrationFailsOrExposeProviderSecrets() {
        String state = service.start(tenant, user, null).authorizationUrl();
        when(client.configureLeads(anyString(), nullable(String.class), anyString(), anyString()))
                .thenThrow(new IllegalStateException("private-access-token"));
        service.complete("code", state, null);
        assertThat(connection.getStatus()).isEqualTo(IntegrationConnectionStatus.ERROR);
        assertThat(connection.getLastError()).doesNotContain("private-access-token");
        assertThat(connection.getConfig()).doesNotContainKey("olxLeadConfigurationId");
    }

    @Test
    void shouldUpdateExistingSubscriptionAndKeepDeliveryCredentialAndPausedState() throws Exception {
        service.complete("code", service.start(tenant, user, null).authorizationUrl(), null);
        String deliveryToken = mapper.readTree(secrets.decrypt(connection.getEncryptedCredentials())).path("webhookToken").asText();
        connection.setStatus(IntegrationConnectionStatus.PAUSED);
        String state = service.start(tenant, user, connection.getId()).authorizationUrl();
        service.complete("code", state, null);
        verify(client).configureLeads(eq("private-access-token"), eq("configuration-id"), anyString(), eq(deliveryToken));
        assertThat(connection.getStatus()).isEqualTo(IntegrationConnectionStatus.PAUSED);
    }

    @Test
    void shouldScopeReconnectToTenantAndRejectWrongTenantState() {
        String state = service.start(tenant, user, null).authorizationUrl();
        assertThatThrownBy(() -> service.start(UUID.randomUUID(), user, connection.getId())).isInstanceOf(ResponseStatusException.class);
        String decoded = new String(Base64.getUrlDecoder().decode(state), java.nio.charset.StandardCharsets.UTF_8);
        String changedTenant = Base64.getUrlEncoder().encodeToString(decoded.replace(tenant.toString(), UUID.randomUUID().toString())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.complete("code", changedTenant, null)).isInstanceOf(ResponseStatusException.class);
        verify(client, never()).exchangeCode(any());
    }

    @Test
    void shouldInvalidatePreviousAttemptWhenReconnecting() {
        String previous = service.start(tenant, user, null).authorizationUrl();
        String current = service.start(tenant, user, connection.getId()).authorizationUrl();
        assertThatThrownBy(() -> service.complete("code", previous, null)).isInstanceOf(ResponseStatusException.class);
        verify(client, never()).exchangeCode(any());
        service.complete("code", current, null);
        assertThat(connection.getStatus()).isEqualTo(IntegrationConnectionStatus.ACTIVE);
    }

    @Test
    void shouldRejectSwitchingAdvertiserWithoutChangingWorkingSubscription() {
        service.complete("code", service.start(tenant, user, null).authorizationUrl(), null);
        String encrypted = connection.getEncryptedCredentials();
        String state = service.start(tenant, user, connection.getId()).authorizationUrl();
        clearInvocations(client);
        when(client.account("private-access-token")).thenReturn(new OlxLeadClient.Account("another@example.com", "Outra conta"));
        service.complete("code", state, null);
        verify(client, never()).configureLeads(anyString(), nullable(String.class), anyString(), anyString());
        assertThat(connection.getStatus()).isEqualTo(IntegrationConnectionStatus.ACTIVE);
        assertThat(connection.getExternalAccountId()).isEqualTo("advertiser@example.com");
        assertThat(connection.getEncryptedCredentials()).isEqualTo(encrypted);
        assertThat(connection.getLastError()).isNotBlank();
    }

    @Test
    void shouldRejectOAuthDeliveriesUntilRegistrationHasCompleted() {
        service.start(tenant, user, null);
        assertThatThrownBy(() -> service.validateWebhook(connection.getInboundKey(), basic("legacy-secret")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldKeepLegacyCanalProAuthentication() {
        service.start(tenant, user, null);
        connection.getConfig().clear();
        service.validateWebhook(connection.getInboundKey(), basic("legacy-secret"));
        assertThatThrownBy(() -> service.validateWebhook(connection.getInboundKey(), basic("wrong"))).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldRejectMissingConfigurationBeforeCreatingConnection() {
        ReflectionTestUtils.setField(service, "publicApiUrl", "http://localhost:39000");
        assertThatThrownBy(() -> service.start(tenant, user, null)).isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    private String basic(String password) {
        return "Basic " + Base64.getEncoder().encodeToString(("provider:" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
