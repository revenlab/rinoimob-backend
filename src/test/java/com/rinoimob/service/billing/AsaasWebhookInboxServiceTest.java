package com.rinoimob.service.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.domain.repository.AsaasWebhookEventRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsaasWebhookInboxServiceTest {

    @Test
    void shouldPersistEventOnceAndAcknowledgeDuplicateDelivery() throws Exception {
        AsaasWebhookEventRepository repository = mock(AsaasWebhookEventRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AsaasWebhookInboxService service = new AsaasWebhookInboxService(repository, objectMapper);
        JsonNode payload = objectMapper.readTree("""
                {
                  "id": "evt_123&456",
                  "event": "PAYMENT_OVERDUE",
                  "dateCreated": "2026-08-24 23:19:33",
                  "account": { "id": "account_123" },
                  "payment": { "id": "pay_123", "customer": "cus_123" }
                }
                """);
        when(repository.insertIfAbsent(
                any(UUID.class), eq("evt_123&456"), eq("PAYMENT_OVERDUE"), eq("account_123"),
                eq("PAYMENT"), eq("pay_123"), any(String.class),
                eq(LocalDateTime.of(2026, 8, 24, 23, 19, 33)), any(LocalDateTime.class)
        )).thenReturn(1, 0);

        assertThat(service.receive(payload)).isTrue();
        assertThat(service.receive(payload)).isFalse();

        verify(repository, org.mockito.Mockito.times(2)).insertIfAbsent(
                any(UUID.class), eq("evt_123&456"), eq("PAYMENT_OVERDUE"), eq("account_123"),
                eq("PAYMENT"), eq("pay_123"), any(String.class),
                eq(LocalDateTime.of(2026, 8, 24, 23, 19, 33)), any(LocalDateTime.class)
        );
    }
}
