package com.rinoimob.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.domain.entity.IntegrationConnection;
import com.rinoimob.domain.enums.IntegrationConnectionStatus;
import com.rinoimob.domain.enums.IntegrationProvider;
import com.rinoimob.domain.repository.IntegrationConnectionRepository;
import com.rinoimob.domain.repository.IntegrationEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntegrationInboxServiceTest {
    @Test
    void shouldUseAtomicEncryptedInsertAndOnlyUpdateConnectionForNewEvent() throws Exception {
        var connections = mock(IntegrationConnectionRepository.class);
        var events = mock(IntegrationEventRepository.class);
        var secrets = new IntegrationSecretService("a-stable-test-key-with-more-than-32-characters");
        var service = new IntegrationInboxService(connections, events, secrets);
        var connection = new IntegrationConnection();
        connection.setId(UUID.randomUUID());
        connection.setTenantId(UUID.randomUUID());
        connection.setProvider(IntegrationProvider.GRUPO_OLX);
        connection.setStatus(IntegrationConnectionStatus.ACTIVE);
        when(connections.findByInboundKeyAndProvider("inbound", IntegrationProvider.GRUPO_OLX))
                .thenReturn(Optional.of(connection));
        when(events.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1, 0);
        var payload = new ObjectMapper().readTree("{\"originLeadId\":\"lead-123\",\"name\":\"Maria\"}");
        service.receiveGrupoOlx("inbound", payload);
        service.receiveGrupoOlx("inbound", payload);
        var encrypted = ArgumentCaptor.forClass(String.class);
        verify(events, times(2)).insertIfAbsent(any(), eq(connection.getTenantId()), eq(connection.getId()),
                eq("GRUPO_OLX"), eq("lead-123"), eq("*"), eq("LEAD"), eq("RECEIVED"), encrypted.capture(), any());
        assertThat(encrypted.getAllValues()).allSatisfy(value -> {
            assertThat(value).doesNotContain("Maria");
            assertThat(secrets.decrypt(value)).contains("Maria");
        });
        verify(connections, times(1)).save(connection);
    }
}
