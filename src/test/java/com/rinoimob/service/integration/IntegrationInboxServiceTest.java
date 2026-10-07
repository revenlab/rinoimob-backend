package com.rinoimob.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.domain.entity.IntegrationConnection;
import com.rinoimob.domain.entity.IntegrationEvent;
import com.rinoimob.domain.enums.IntegrationConnectionStatus;
import com.rinoimob.domain.enums.IntegrationEventStatus;
import com.rinoimob.domain.enums.IntegrationProvider;
import com.rinoimob.domain.repository.IntegrationConnectionRepository;
import com.rinoimob.domain.repository.IntegrationEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntegrationInboxServiceTest {
    @Test
    void shouldPersistEncryptedGrupoOlxEventOnlyOnce() throws Exception {
        IntegrationConnectionRepository connectionRepository = mock(IntegrationConnectionRepository.class);
        IntegrationEventRepository eventRepository = mock(IntegrationEventRepository.class);
        IntegrationSecretService secretService = new IntegrationSecretService("a-stable-test-key-with-more-than-32-characters");
        IntegrationInboxService service = new IntegrationInboxService(connectionRepository, eventRepository, secretService);

        IntegrationConnection connection = new IntegrationConnection();
        connection.setId(UUID.randomUUID());
        connection.setTenantId(UUID.randomUUID());
        connection.setProvider(IntegrationProvider.GRUPO_OLX);
        connection.setStatus(IntegrationConnectionStatus.ACTIVE);
        when(connectionRepository.findByInboundKeyAndProvider("inbound", IntegrationProvider.GRUPO_OLX))
                .thenReturn(Optional.of(connection));

        IntegrationEvent existing = new IntegrationEvent();
        when(eventRepository.findByConnectionIdAndExternalEventId(connection.getId(), "lead-123"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(eventRepository.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        ObjectMapper objectMapper = new ObjectMapper();
        var payload = objectMapper.readTree("{\"originLeadId\":\"lead-123\",\"name\":\"Maria\"}");
        service.receiveGrupoOlx("inbound", payload);
        service.receiveGrupoOlx("inbound", payload);

        ArgumentCaptor<IntegrationEvent> captor = ArgumentCaptor.forClass(IntegrationEvent.class);
        verify(eventRepository, times(1)).saveAndFlush(captor.capture());
        IntegrationEvent persisted = captor.getValue();
        assertThat(persisted.getTenantId()).isEqualTo(connection.getTenantId());
        assertThat(persisted.getStatus()).isEqualTo(IntegrationEventStatus.RECEIVED);
        assertThat(persisted.getExternalEventId()).isEqualTo("lead-123");
        assertThat(persisted.getEncryptedPayload()).doesNotContain("Maria");
        assertThat(secretService.decrypt(persisted.getEncryptedPayload())).contains("Maria");
    }
}
