package com.rinoimob.api.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MetaLeadAdsClientTest {
    @Test
    void shouldRejectSubscriptionWithoutExplicitMetaConfirmation() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        ObjectMapper mapper = new ObjectMapper();
        MetaLeadAdsClient client = new MetaLeadAdsClient(restTemplate, mapper, "v23.0", "app", "secret", "https://example.test/callback");
        when(restTemplate.postForEntity(anyString(), any(), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(mapper.readTree("{\"success\":false}")),
                        ResponseEntity.ok(mapper.readTree("{}")), ResponseEntity.ok().build(),
                        ResponseEntity.ok(mapper.readTree("{\"success\":true}")));
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> client.subscribePage("page", "token")).isInstanceOf(IllegalStateException.class);
        }
        assertThatCode(() -> client.subscribePage("page", "token")).doesNotThrowAnyException();
    }
    @Test
    void shouldDiscoverOnlyExplicitlyGrantedPagesWhenAccountsListIsEmpty() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MetaLeadAdsClient client = new MetaLeadAdsClient(restTemplate, new ObjectMapper(), "v23.0", "app", "secret", "https://example.test/callback");
        server.expect(requestTo(containsString("/me/accounts?")))
                .andExpect(header("Authorization", "Bearer user-token"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/debug_token?")))
                .andExpect(queryParam("input_token", "user-token"))
                .andExpect(header("Authorization", "Bearer app|secret"))
                .andRespond(withSuccess("{\"data\":{\"is_valid\":true,\"app_id\":\"app\",\"granular_scopes\":[{\"scope\":\"pages_show_list\",\"target_ids\":[\"100\",\"200\",\"100\"]},{\"scope\":\"leads_retrieval\",\"target_ids\":[\"999\"]}]}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/100?")))
                .andExpect(header("Authorization", "Bearer user-token"))
                .andRespond(withSuccess("{\"id\":\"100\",\"name\":\"First\",\"access_token\":\"page-100\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/200?")))
                .andRespond(withSuccess("{\"id\":\"200\",\"name\":\"Second\",\"access_token\":\"page-200\"}", MediaType.APPLICATION_JSON));
        var pages = client.listPages("user-token");
        assertThat(pages).extracting(page -> page.get("id")).containsExactly("100", "200");
        assertThat(pages.get(0).get("accessToken")).isEqualTo("page-100");
        server.verify();
    }

    @Test
    void shouldReadAllAccountsPagesUsingCursors() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MetaLeadAdsClient client = new MetaLeadAdsClient(restTemplate, new ObjectMapper(), "v23.0", "app", "secret", "https://example.test/callback");
        server.expect(requestTo(containsString("/me/accounts?")))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"100\",\"name\":\"First\",\"access_token\":\"page-100\"}],\"paging\":{\"next\":\"https://graph.facebook.com/next\",\"cursors\":{\"after\":\"next-cursor\"}}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/me/accounts?")))
                .andExpect(queryParam("after", "next-cursor"))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"200\",\"name\":\"Second\",\"access_token\":\"page-200\"}]}", MediaType.APPLICATION_JSON));
        assertThat(client.listPages("user-token")).extracting(page -> page.get("id")).containsExactly("100", "200");
        server.verify();
    }

    @Test
    void shouldRejectFallbackWhenTokenBelongsToAnotherApp() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MetaLeadAdsClient client = new MetaLeadAdsClient(restTemplate, new ObjectMapper(), "v23.0", "app", "secret", "https://example.test/callback");
        server.expect(requestTo(containsString("/me/accounts?")))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/debug_token?")))
                .andRespond(withSuccess("{\"data\":{\"is_valid\":true,\"app_id\":\"other-app\",\"granular_scopes\":[{\"scope\":\"pages_show_list\",\"target_ids\":[\"100\"]}]}}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.listPages("user-token")).isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    void shouldRequestPagesManageAdsDuringAuthorization() {
        var client = new MetaLeadAdsClient(new RestTemplate(), new ObjectMapper(), "v23.0", "app", "secret", "https://example.test/callback");
        assertThat(client.authorizationUrl("state")).contains("pages_manage_ads");
    }

    @Test
    void shouldExplainMissingFormsPermissionWithoutReturningInternalError() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        var client = new MetaLeadAdsClient(restTemplate, new ObjectMapper(), "v23.0", "app", "secret", "https://example.test/callback");
        server.expect(requestTo(containsString("/100/leadgen_forms?")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Requires pages_manage_ads permission\",\"code\":200}}"));
        assertThatThrownBy(() -> client.listForms("100", "page-token"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).contains("pages_manage_ads", "reconecte");
                });
        server.verify();
    }

    @Test
    void shouldFilterRecoveryWindowAndEncodeCursorExactlyOnce() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        var client = new MetaLeadAdsClient(restTemplate, new ObjectMapper(), "v23.0", "app", "secret", "https://example.test/callback");
        var from = java.time.Instant.parse("2026-10-20T12:00:00Z");
        var until = java.time.Instant.parse("2026-10-20T13:00:00Z");
        server.expect(requestTo(containsString("/123/leads?")))
                .andExpect(request -> {
                    String raw = request.getURI().getRawQuery();
                    assertThat(raw).doesNotContain("%257B");
                    String decoded = java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
                    assertThat(decoded).contains("time_created", "GREATER_THAN", String.valueOf(from.getEpochSecond() - 1), "after=cursor+/=");
                })
                .andExpect(header("Authorization", "Bearer page-token"))
                .andRespond(withSuccess("""
                        {"data":[
                          {"id":"1","form_id":"123","created_time":"2026-10-20T12:00:00+0000"},
                          {"id":"2","form_id":"123","created_time":"2026-10-20T13:00:00+0000"},
                          {"id":"3","form_id":"123","created_time":"2026-10-20T11:59:59+0000"},
                          {"id":"4","form_id":"123","created_time":"2026-10-20T13:00:01+0000"}
                        ],"paging":{"next":"https://ignored.example/next","cursors":{"after":"cursor-2"}}}
                        """, MediaType.APPLICATION_JSON));
        var page = client.listFormLeads("123", "page-token", from, until, "cursor+/=");
        assertThat(page.leads()).extracting(MetaLeadAdsClient.LeadReference::id).containsExactly("1", "2");
        assertThat(page.nextCursor()).isEqualTo("cursor-2");
        server.verify();
    }

    @Test
    void shouldClassifyExpiredTokenWithoutLeakingProviderResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        var client = new MetaLeadAdsClient(restTemplate, new ObjectMapper(), "v23.0", "app", "secret", "https://example.test/callback");
        server.expect(requestTo(containsString("/123/leads?")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":190,\"message\":\"secret-provider-details\"}}"));
        assertThatThrownBy(() -> client.listFormLeads("123", "page-token", java.time.Instant.EPOCH, java.time.Instant.now(), null))
                .isInstanceOfSatisfying(MetaLeadAdsClient.LeadReadException.class, exception -> {
                    assertThat(exception.isAuthorizationFailure()).isTrue();
                    assertThat(exception.getMessage()).doesNotContain("secret-provider-details");
                    assertThat(exception.getCause()).isNull();
                });
        server.verify();
    }

}
