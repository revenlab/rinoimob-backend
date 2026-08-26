package com.rinoimob.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.service.billing.AsaasWebhookInboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AsaasWebhookControllerTest {

    private AsaasWebhookInboxService inboxService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        inboxService = mock(AsaasWebhookInboxService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AsaasWebhookController(inboxService, new ObjectMapper(), "webhook-secret")
        ).build();
    }

    @Test
    void shouldAcknowledgePersistedWebhookBeforeDomainProcessing() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/asaas")
                        .header("asaas-access-token", "webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"evt_123\",\"event\":\"PAYMENT_CONFIRMED\"}"))
                .andExpect(status().isAccepted());

        verify(inboxService).receive(any());
    }

    @Test
    void shouldRejectInvalidJsonWithoutPersistingIt() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/asaas")
                        .header("asaas-access-token", "webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectInvalidWebhookToken() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/asaas")
                        .header("asaas-access-token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
