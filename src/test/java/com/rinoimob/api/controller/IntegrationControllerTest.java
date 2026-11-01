package com.rinoimob.api.controller;

import com.rinoimob.service.integration.GrupoOlxPropertyFeedService;
import com.rinoimob.service.integration.IntegrationManagementService;
import com.rinoimob.service.integration.OlxOAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IntegrationControllerTest {
    private final IntegrationManagementService service = mock(IntegrationManagementService.class);
    private final OlxOAuthService olx = mock(OlxOAuthService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        IntegrationController controller = new IntegrationController(service, mock(GrupoOlxPropertyFeedService.class), olx);
        ReflectionTestUtils.setField(controller, "frontendUrl", "http://localhost:5173");
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturnFromOlxWithoutExposingOAuthCode() throws Exception {
        UUID id = UUID.randomUUID();
        when(olx.complete("secret-code", "state", null)).thenReturn(id);
        mvc.perform(get("/api/v1/integrations/grupo-olx/oauth/callback").param("code", "secret-code").param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:5173/integracoes?olx=returned&connectionId=" + id));
    }

    @Test
    void shouldReturnOlxInvalidStateToFrontend() throws Exception {
        when(olx.complete(null, null, null)).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST));
        mvc.perform(get("/api/v1/integrations/grupo-olx/oauth/callback"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("http://localhost:5173/integracoes?olx=error"));
    }

    @Test
    void shouldReturnToFrontendAfterSuccessfulCallback() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.completeMetaOAuth("code", "state")).thenReturn(id);
        mvc.perform(get("/api/v1/integrations/meta/oauth/callback").param("code", "code").param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:5173/integracoes?meta=returned&connectionId=" + id));
    }

    @Test
    void shouldReturnToFrontendWhenCallbackStateWasAlreadyConsumed() throws Exception {
        when(service.completeMetaOAuth("code", "old-state"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Meta OAuth state"));
        mvc.perform(get("/api/v1/integrations/meta/oauth/callback").param("code", "code").param("state", "old-state"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:5173/integracoes?meta=error"));
    }

    @Test
    void shouldReturnToFrontendWhenStateIsMissingWithoutProcessingCode() throws Exception {
        mvc.perform(get("/api/v1/integrations/meta/oauth/callback").param("code", "code"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:5173/integracoes?meta=error"));
        verifyNoInteractions(service);
    }
}
