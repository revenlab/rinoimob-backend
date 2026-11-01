package com.rinoimob.api.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Map;

@Component
public class OlxLeadClient {
    private final RestTemplate http;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public OlxLeadClient(@Qualifier("olxRestTemplate") RestTemplate http,
                         @Value("${integrations.grupo-olx.client-id:}") String clientId,
                         @Value("${integrations.grupo-olx.client-secret:}") String clientSecret,
                         @Value("${integrations.grupo-olx.redirect-uri:}") String redirectUri) {
        this.http = http;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public String authorizationUrl(String state) {
        if (clientId.isBlank() || clientSecret.isBlank() || redirectUri.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Configure as credenciais OAuth da OLX no servidor antes de conectar.");
        }
        return UriComponentsBuilder.fromUriString("https://auth.olx.com.br/oauth")
                .queryParam("client_id", clientId).queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code").queryParam("scope", "autoservice basic_user_info")
                .queryParam("state", state).build().encode().toUriString();
    }

    public String exchangeCode(String code) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return required(http.postForObject("https://auth.olx.com.br/oauth/token",
                new HttpEntity<>(form, headers), JsonNode.class), "access_token");
    }

    public record Account(String email, String name) {}

    public Account account(String token) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        JsonNode response = http.postForObject("https://apps.olx.com.br/oauth_api/basic_user_info",
                new HttpEntity<>(Map.of("access_token", token), headers), JsonNode.class);
        return new Account(required(response, "user_email"), required(response, "user_name"));
    }

    public String configureLeads(String token, String configurationId, String webhookUrl, String deliveryToken) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        boolean create = configurationId == null;
        URI uri = UriComponentsBuilder.fromUriString("https://apps.olx.com.br/autoservice/v1/lead")
                .path(create ? "" : "/{id}").buildAndExpand(create ? Map.of() : Map.of("id", configurationId)).encode().toUri();
        JsonNode response = http.exchange(uri, create ? HttpMethod.POST : HttpMethod.PUT,
                new HttpEntity<>(Map.of("url", webhookUrl, "token", deliveryToken), headers), JsonNode.class).getBody();
        String id = required(response, "id");
        if (!create && !configurationId.equals(id)) throw new IllegalStateException("Unexpected OLX configuration");
        return id;
    }

    private String required(JsonNode node, String field) {
        if (node == null || node.path(field).asText().isBlank()) {
            throw new IllegalStateException("Incomplete OLX response");
        }
        return node.path(field).asText();
    }
}
