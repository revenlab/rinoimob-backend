package com.rinoimob.api.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestClientResponseException;
import java.util.Map;

@Component
public class MetaLeadAdsClient {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiVersion;
    private final String appId;
    private final String appSecret;
    private final String redirectUri;

    public MetaLeadAdsClient(@Qualifier("metaRestTemplate") RestTemplate restTemplate, ObjectMapper objectMapper,
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
                .queryParam("scope", "leads_retrieval,pages_show_list,pages_read_engagement,pages_manage_metadata,pages_manage_ads")
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
        Map<String, Map<String, String>> pages = new LinkedHashMap<>();
        String after = null;
        Set<String> cursors = new HashSet<>();
        do {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(graphUrl("me/accounts"))
                    .queryParam("fields", "id,name,access_token").queryParam("limit", 100);
            if (after != null) builder.queryParam("after", after);
            JsonNode response = getAuthorized(builder.build().encode().toUriString(), userToken);
            response.path("data").forEach(page -> addPage(pages, page));
            after = response.path("paging").hasNonNull("next")
                    ? response.path("paging").path("cursors").path("after").asText(null) : null;
            if (after != null && !cursors.add(after)) {
                throw new IllegalStateException("Meta returned a repeated page cursor");
            }
        } while (after != null);

        // Some Meta business authorizations grant explicit Pages but return no /me/accounts entries.
        // Only query Page IDs explicitly granted to this app under pages_show_list.
        if (pages.isEmpty()) {
            String debugUrl = UriComponentsBuilder.fromUriString(graphUrl("debug_token"))
                    .queryParam("input_token", userToken).build().encode().toUriString();
            JsonNode debug = getAuthorized(debugUrl, appId + "|" + appSecret).path("data");
            if (!debug.path("is_valid").asBoolean(false) || !appId.equals(debug.path("app_id").asText())) {
                throw new IllegalStateException("Meta authorization is invalid for this app");
            }
            Set<String> grantedPageIds = new java.util.LinkedHashSet<>();
            debug.path("granular_scopes").forEach(grant -> {
                if ("pages_show_list".equals(grant.path("scope").asText())) {
                    grant.path("target_ids").forEach(id -> grantedPageIds.add(id.asText()));
                }
            });
            for (String pageId : grantedPageIds) {
                if (!pageId.matches("[0-9]+")) continue;
                addPage(pages, get(pageId, userToken, "id,name,access_token"));
            }
        }
        return new ArrayList<>(pages.values());
    }

    private void addPage(Map<String, Map<String, String>> pages, JsonNode page) {
        String id = requiredText(page, "id");
        pages.put(id, Map.of("id", id, "name", requiredText(page, "name"),
                "accessToken", requiredText(page, "access_token")));
    }

    public List<Map<String, String>> listForms(String pageId, String pageToken) {
        JsonNode data;
        try {
            data = get(pageId + "/leadgen_forms", pageToken, "id,name,status").path("data");
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A Meta não autorizou a consulta dos formulários. Habilite pages_manage_ads no aplicativo Meta e reconecte a página concedendo essa permissão.");
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A autorização da Meta expirou. Reconecte a página para consultar os formulários.");
        }
        List<Map<String, String>> forms = new ArrayList<>();
        data.forEach(form -> forms.add(Map.of(
                "id", form.path("id").asText(),
                "name", form.path("name").asText(),
                "status", form.path("status").asText("")
        )));
        return forms;
    }

    public record LeadReference(String id, String formId, Instant createdAt) {}
    public record LeadPage(List<LeadReference> leads, String nextCursor) {}

    public static class LeadReadException extends RuntimeException {
        private final boolean authorizationFailure;
        private final boolean invalidCursor;

        public LeadReadException(boolean authorizationFailure, boolean invalidCursor) {
            super(authorizationFailure ? "A Meta recusou a autorização. Reconecte a página e confira as permissões."
                    : "Não foi possível conferir os leads na Meta. A consulta será tentada novamente.");
            this.authorizationFailure = authorizationFailure;
            this.invalidCursor = invalidCursor;
        }

        public boolean isAuthorizationFailure() { return authorizationFailure; }
        public boolean isInvalidCursor() { return invalidCursor; }
    }

    public LeadPage listFormLeads(String formId, String pageToken, Instant from, Instant until, String after) {
        if (!formId.matches("[0-9]+") || from.isAfter(until)) {
            throw new IllegalArgumentException("Invalid Meta lead reconciliation window");
        }
        // Strict Graph operators: expand one second at the boundaries, then filter locally too.
        String filtering = "[{\"field\":\"time_created\",\"operator\":\"GREATER_THAN\",\"value\":"
                + (from.getEpochSecond() - 1) + "},{\"field\":\"time_created\",\"operator\":\"LESS_THAN\",\"value\":"
                + (until.getEpochSecond() + 1) + "}]";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(graphUrl(formId + "/leads"))
                .queryParam("fields", "id,form_id,created_time").queryParam("limit", 100)
                .queryParam("filtering", "{filtering}");
        Map<String, String> variables = new java.util.HashMap<>();
        variables.put("filtering", filtering);
        if (after != null) {
            builder.queryParam("after", "{after}");
            variables.put("after", after);
        }
        JsonNode response;
        try {
            response = getAuthorized(builder.encode().buildAndExpand(variables).toUriString(), pageToken);
        } catch (RestClientResponseException exception) {
            int code = 0;
            try { code = objectMapper.readTree(exception.getResponseBodyAsString()).path("error").path("code").asInt(); }
            catch (Exception ignored) { /* Never propagate the provider response or request URL. */ }
            boolean auth = code == 190 || code == 200 || code == 10
                    || exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403;
            throw new LeadReadException(auth, code == 100 && after != null);
        }
        if (!response.path("data").isArray()) throw new IllegalStateException("Meta did not return a lead list");
        List<LeadReference> leads = new ArrayList<>();
        for (JsonNode node : response.path("data")) {
            String id = requiredText(node, "id");
            String receivedFormId = requiredText(node, "form_id");
            if (!formId.equals(receivedFormId)) throw new IllegalStateException("Meta lead belongs to another form");
            Instant created = OffsetDateTime.parse(requiredText(node, "created_time"),
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXX")).toInstant();
            if (!created.isBefore(from) && !created.isAfter(until)) leads.add(new LeadReference(id, formId, created));
        }
        String next = response.path("paging").hasNonNull("next")
                ? response.path("paging").path("cursors").path("after").asText(null) : null;
        if (response.path("paging").hasNonNull("next") && (next == null || next.isBlank() || next.equals(after))) {
            throw new IllegalStateException("Meta did not advance the lead cursor");
        }
        return new LeadPage(List.copyOf(leads), next);
    }

    public void subscribePage(String pageId, String pageToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("subscribed_fields", "leadgen");
        body.add("access_token", pageToken);
        JsonNode response = restTemplate.postForEntity(graphUrl(pageId + "/subscribed_apps"), body, JsonNode.class).getBody();
        if (response == null || !response.path("success").asBoolean(false)) {
            throw new IllegalStateException("Meta did not confirm the page subscription");
        }
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
                .build().encode().toUriString();
        return getAuthorized(url, token);
    }

    private JsonNode getAuthorized(String url, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        JsonNode response = restTemplate.exchange(java.net.URI.create(url), HttpMethod.GET, new HttpEntity<Void>(headers), JsonNode.class).getBody();
        if (response == null) throw new IllegalStateException("Meta returned an empty response");
        return response;
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
