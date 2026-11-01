package com.rinoimob.api.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OlxLeadClientTest {
    private final RestTemplate http = new RestTemplate();
    private MockRestServiceServer server;
    private OlxLeadClient client;

    @BeforeEach
    void setup() {
        server = MockRestServiceServer.bindTo(http).build();
        client = new OlxLeadClient(http, "client-id", "client-secret", "https://api.example.com/callback");
    }

    @Test
    void shouldRequestRequiredScopesAndExchangeFormEncodedCode() {
        assertThat(client.authorizationUrl("nonce")).contains("scope=autoservice%20basic_user_info", "state=nonce")
                .doesNotContain("client-secret");
        server.expect(requestTo("https://auth.olx.com.br/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("grant_type=authorization_code"),
                        org.hamcrest.Matchers.containsString("client_id=client-id"),
                        org.hamcrest.Matchers.containsString("client_secret=client-secret"),
                        org.hamcrest.Matchers.containsString("code=a%2Bb"),
                        org.hamcrest.Matchers.containsString("redirect_uri=https%3A%2F%2Fapi.example.com%2Fcallback"))))
                .andRespond(withSuccess("{\"access_token\":\"token\",\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));
        assertThat(client.exchangeCode("a+b")).isEqualTo("token");
        server.verify();
    }

    @Test
    void shouldCreateAndUpdateWebhookUsingBearerAndDeliveryCredential() {
        server.expect(requestTo("https://apps.olx.com.br/autoservice/v1/lead"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer access-token"))
                .andExpect(content().json("{\"url\":\"https://api.example.com/webhook\",\"token\":\"delivery-token\"}"))
                .andRespond(withSuccess("{\"id\":\"id-123\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://apps.olx.com.br/autoservice/v1/lead/id-123"))
                .andExpect(method(HttpMethod.PUT)).andExpect(header("Authorization", "Bearer new-access-token"))
                .andExpect(content().json("{\"url\":\"https://api.example.com/webhook\",\"token\":\"delivery-token\"}"))
                .andRespond(withSuccess("{\"id\":\"id-123\"}", MediaType.APPLICATION_JSON));
        assertThat(client.configureLeads("access-token", null, "https://api.example.com/webhook", "delivery-token")).isEqualTo("id-123");
        assertThat(client.configureLeads("new-access-token", "id-123", "https://api.example.com/webhook", "delivery-token")).isEqualTo("id-123");
        server.verify();
    }

    @Test
    void shouldReadAccountAndRejectIncompleteRegistrationResponse() {
        server.expect(requestTo("https://apps.olx.com.br/oauth_api/basic_user_info"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().json("{\"access_token\":\"token\"}"))
                .andRespond(withSuccess("{\"user_email\":\"account@example.com\",\"user_name\":\"Nome\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://apps.olx.com.br/autoservice/v1/lead"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThat(client.account("token")).isEqualTo(new OlxLeadClient.Account("account@example.com", "Nome"));
        assertThatThrownBy(() -> client.configureLeads("token", null, "https://api.example.com/webhook", "delivery-token"))
                .isInstanceOf(IllegalStateException.class);
        server.verify();
    }
}
