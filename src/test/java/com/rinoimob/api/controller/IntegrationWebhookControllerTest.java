package com.rinoimob.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.api.client.MetaLeadAdsClient;
import com.rinoimob.service.integration.IntegrationInboxService;
import com.rinoimob.service.integration.OlxOAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IntegrationWebhookControllerTest {
    private static final String META_SECRET = "meta-secret";
    private static final String OLX_SECRET = "olx-secret";

    private IntegrationInboxService inboxService;
    private OlxOAuthService olx;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        inboxService = mock(IntegrationInboxService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        MetaLeadAdsClient metaClient = new MetaLeadAdsClient(
                new RestTemplate(), objectMapper, "v23.0", "app-id", META_SECRET, "https://app.example.com/callback"
        );
        olx = mock(OlxOAuthService.class);
        IntegrationWebhookController controller = new IntegrationWebhookController(inboxService, metaClient, objectMapper, olx);
        ReflectionTestUtils.setField(controller, "metaVerifyToken", "verify-token");
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldVerifyMetaChallengeWithConfiguredToken() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks/integrations/meta")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "verify-token")
                        .param("hub.challenge", "challenge-123"))
                .andExpect(status().isOk())
                .andExpect(content().string("challenge-123"));
    }

    @Test
    void shouldAcceptSignedMetaEventAndPersistItBeforeProcessing() throws Exception {
        byte[] body = "{\"object\":\"page\",\"entry\":[]}".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/webhooks/integrations/meta")
                        .header("X-Hub-Signature-256", signature(body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        verify(inboxService).receiveMeta(any());
    }

    @Test
    void shouldRejectMetaEventWithInvalidSignature() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/integrations/meta")
                        .header("X-Hub-Signature-256", "sha256=invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAcceptGrupoOlxEventWithBasicSecret() throws Exception {
        String authorization = "Basic " + Base64.getEncoder()
                .encodeToString(("provider:" + OLX_SECRET).getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(post("/api/v1/webhooks/integrations/grupo-olx/inbound-123")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originLeadId\":\"lead-123\"}"))
                .andExpect(status().isAccepted());

        verify(inboxService).receiveGrupoOlx(eq("inbound-123"), any());
        verify(olx).validateWebhook("inbound-123", authorization);
    }

    @Test
    void shouldRejectOlxBeforePersistingWhenDeliveryCredentialIsInvalid() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .when(olx).validateWebhook("inbound-123", null);
        mockMvc.perform(post("/api/v1/webhooks/integrations/grupo-olx/inbound-123")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"externalId\":\"123\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(inboxService);
    }

    private String signature(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(META_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
