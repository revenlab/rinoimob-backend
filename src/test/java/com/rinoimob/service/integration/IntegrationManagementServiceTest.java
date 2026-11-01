package com.rinoimob.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.api.client.MetaLeadAdsClient;
import com.rinoimob.domain.dto.integration.ActivateMetaConnectionRequest;
import com.rinoimob.domain.entity.IntegrationConnection;
import com.rinoimob.domain.enums.*;
import com.rinoimob.domain.repository.*;
import com.rinoimob.service.billing.TenantPlanAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntegrationManagementServiceTest {
    private final IntegrationConnectionRepository repository = mock(IntegrationConnectionRepository.class);
    private final MetaLeadAdsClient client = mock(MetaLeadAdsClient.class);
    private final IntegrationConnection connection = new IntegrationConnection();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final IntegrationManagementService service = new IntegrationManagementService(repository,
            mock(IntegrationRouteRepository.class), mock(IntegrationEventRepository.class),
            mock(LeadPipelineRepository.class), mock(LeadPoolRepository.class), mock(UserRepository.class),
            mock(PropertyRepository.class), mock(TenantPlanAccessService.class),
            new IntegrationSecretService("a-stable-test-key-with-more-than-32-characters"), client, new ObjectMapper());

    @BeforeEach
    void setup() {
        connection.setId(UUID.randomUUID());
        connection.setTenantId(tenantId);
        connection.setProvider(IntegrationProvider.META_LEAD_ADS);
        when(repository.findByIdAndTenantId(connection.getId(), tenantId)).thenReturn(Optional.of(connection));
        when(repository.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(client.authorizationUrl(anyString())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void shouldNotCreateConnectionWhenMetaIsNotConfigured() {
        when(client.authorizationUrl(anyString())).thenThrow(new IllegalStateException("Not configured"));
        assertThatThrownBy(() -> service.startMetaOAuth(tenantId, userId)).isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldReconnectSameConnectionAndInvalidatePreviousState() {
        String oldState = reconnect();
        assertThat(reconnect()).isNotEqualTo(oldState);
        assertThat(connection.getStatus()).isEqualTo(IntegrationConnectionStatus.CONNECTING);
        assertThatThrownBy(() -> service.completeMetaOAuth("code", oldState)).isInstanceOf(ResponseStatusException.class);
        verify(repository, times(2)).save(connection);
    }

    @Test
    void shouldNotReconnectAnotherTenantsConnection() {
        assertThatThrownBy(() -> service.reconnectMetaOAuth(UUID.randomUUID(), userId, connection.getId()))
                .isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRecordCancelledAuthorizationAndConsumeState() {
        String state = reconnect();
        assertThat(service.completeMetaOAuth(null, state)).isEqualTo(connection.getId());
        assertThat(connection.getStatus()).isEqualTo(IntegrationConnectionStatus.ERROR);
        assertThat(connection.getLastError()).contains("Reconecte");
        assertThatThrownBy(() -> service.completeMetaOAuth("code", state)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldKeepAuthorizationPendingUntilPageSubscriptionIsConfirmed() {
        authorize();
        assertThat(connection.getStatus()).isEqualTo(IntegrationConnectionStatus.CONNECTING);
        verify(client, never()).subscribePage(anyString(), anyString());
    }

    @Test
    void shouldRecordExchangeFailureWithoutLeakingProviderDetails() {
        String state = reconnect();
        when(client.exchangeCode("code")).thenThrow(new IllegalStateException("secret-token"));
        service.completeMetaOAuth("code", state);
        assertThat(connection.getStatus()).isEqualTo(IntegrationConnectionStatus.ERROR);
        assertThat(connection.getLastError()).doesNotContain("secret-token");
        assertThat(connection.getEncryptedCredentials()).isNull();
    }

    @Test
    void shouldActivateOnlyAfterSuccessfulPageSubscription() {
        authorize();
        pages();
        var response = service.activateMetaPage(tenantId, connection.getId(), new ActivateMetaConnectionRequest("page", "Page"));
        assertThat(response.status()).isEqualTo(IntegrationConnectionStatus.ACTIVE);
        assertThat(response.metaAuthorized()).isTrue();
        verify(client).subscribePage("page", "page-token");
    }

    @Test
    void shouldKeepFailedSubscriptionInactiveWithSafeError() {
        authorize();
        pages();
        doThrow(new IllegalStateException("secret-token")).when(client).subscribePage("page", "page-token");
        assertThatThrownBy(() -> service.activateMetaPage(tenantId, connection.getId(), new ActivateMetaConnectionRequest("page", "Page")))
                .isInstanceOf(ResponseStatusException.class).hasMessageNotContaining("secret-token");
        assertThat(connection.getStatus()).isEqualTo(IntegrationConnectionStatus.ERROR);
    }

    @Test
    void shouldRejectManualActivationBeforePageConfirmation() {
        authorize();
        assertThatThrownBy(() -> service.updateStatus(tenantId, connection.getId(), IntegrationConnectionStatus.ACTIVE))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(connection.getStatus()).isEqualTo(IntegrationConnectionStatus.CONNECTING);
    }

    @Test
    void shouldNotActivatePageWithoutAccessToLeadForms() {
        authorize();
        pages();
        when(client.listForms("page", "page-token")).thenThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "Habilite pages_manage_ads e reconecte."));
        assertThatThrownBy(() -> service.activateMetaPage(tenantId, connection.getId(), new ActivateMetaConnectionRequest("page", "Page")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("pages_manage_ads");
        assertThat(connection.getStatus()).isEqualTo(IntegrationConnectionStatus.ERROR);
        verify(client, never()).subscribePage(anyString(), anyString());
    }

    @Test
    void shouldPersistFormsPermissionFailureForPreviouslyActiveConnection() {
        authorize();
        pages();
        service.activateMetaPage(tenantId, connection.getId(), new ActivateMetaConnectionRequest("page", "Page"));
        when(client.listForms("page", "page-token")).thenThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "Habilite pages_manage_ads e reconecte."));
        assertThatThrownBy(() -> service.listMetaForms(tenantId, connection.getId())).isInstanceOf(ResponseStatusException.class);
        assertThat(connection.getStatus()).isEqualTo(IntegrationConnectionStatus.ERROR);
        assertThat(connection.getLastError()).contains("pages_manage_ads");
    }

    private String reconnect() {
        return service.reconnectMetaOAuth(tenantId, userId, connection.getId()).authorizationUrl();
    }

    private void authorize() {
        String state = reconnect();
        when(client.exchangeCode("code")).thenReturn("token");
        service.completeMetaOAuth("code", state);
    }

    private void pages() {
        when(client.listPages("token")).thenReturn(List.of(Map.of("id", "page", "name", "Page", "accessToken", "page-token")));
    }
}
