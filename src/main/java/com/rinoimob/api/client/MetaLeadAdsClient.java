package com.rinoimob.api.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MetaLeadAdsClient {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiVersion;
    private final String appId;
    private final String appSecret;
    private final String redirectUri;

    public MetaLeadAdsClient(RestTemplate restTemplate, ObjectMapper objectMapper,
                             @Value("${integrations.meta.api-version:v23.0}") String apiVersion,
                             @Value("${integrations.meta.app-id:}") String appId,
                             @Value("${integrations.meta.app-secret:}") String appSecret,
                             @Value("${integrations.meta.redirect-uri:}") String redirectUri) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiVersion = apiVersion;
        this.appId = appId;
        this.appSecret = appSecret;
        this.redirectUri = redirectUri;
    }

    public String authorizationUrl(String state) {
        requireConfigured();
        return UriComponentsBuilder.fromUriString("https://www.facebook.com/" + apiVersion + "/dialog/oauth")
                .queryParam("client_id", appId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .queryParam("scope", "leads_retrieval,pages_show_list,pages_read_engagement,pages_manage_metadata")
                .build().encode().toUriString();
    }

    public String exchangeCode(String code) {
        requireConfigured();
        String url = UriComponentsBuilder.fromUriString(graphUrl("oauth/access_token"))
                .queryParam("client_id", appId)
                .queryParam("client_secret", appSecret)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("code", code)
                .build().encode().toUriString();
        JsonNode response = restTemplate.getForObject(url, JsonNode.class);
        return requiredText(response, "access_token");
    }

    public List<Map<String, String>> listPages(String userToken) {
        JsonNode data = get("me/accounts", userToken, "id,name,access_token").path("data");
        List<Map<String, String>> pages = new ArrayList<>();
        data.forEach(page -> pages.add(Map.of(
                "id", page.path("id").asText(),
                "name", page.path("name").asText(),
                "accessToken", page.path("access_token").asText()
        )));
        return pages;
    }

    public List<Map<String, String>> listForms(String pageId, String pageToken) {
        JsonNode data = get(pageId + "/leadgen_forms", pageToken, "id,name,status").path("data");
        List<Map<String, String>> forms = new ArrayList<>();
        data.forEach(form -> forms.add(Map.of(
                "id", form.path("id").asText(),
                "name", form.path("name").asText(),
                "status", form.path("status").asText("")
        )));
        return forms;
    }

    public void subscribePage(String pageId, String pageToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("subscribed_fields", "leadgen");
        body.add("access_token", pageToken);
        restTemplate.postForEntity(graphUrl(pageId + "/subscribed_apps"), body, JsonNode.class);
    }

    public JsonNode getLead(String leadgenId, String pageToken) {
        return get(leadgenId, pageToken,
                "id,created_time,ad_id,ad_name,campaign_id,campaign_name,form_id,field_data");
    }

    public String appSecret() {
        return appSecret;
    }

    private JsonNode get(String path, String token, String fields) {
        String url = UriComponentsBuilder.fromUriString(graphUrl(path))
                .queryParam("fields", fields)
                .queryParam("access_token", token)
                .build().encode().toUriString();
        return restTemplate.getForObject(url, JsonNode.class);
    }

    private String graphUrl(String path) {
        return "https://graph.facebook.com/" + apiVersion + "/" + path;
    }

    private String requiredText(JsonNode node, String field) {
        if (node == null || node.path(field).asText().isBlank()) {
            throw new IllegalStateException("Meta response missing " + field);
        }
        return node.path(field).asText();
    }

    private void requireConfigured() {
        if (appId.isBlank() || appSecret.isBlank() || redirectUri.isBlank()) {
            throw new IllegalStateException("Meta Lead Ads integration is not configured");
        }
    }
}
