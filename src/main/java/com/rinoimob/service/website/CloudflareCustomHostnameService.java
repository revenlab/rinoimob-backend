package com.rinoimob.service.website;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class CloudflareCustomHostnameService {

    private final RestTemplate restTemplate;
    private final String apiToken;
    private final String zoneId;
    private final String targetHostname;

    public CloudflareCustomHostnameService(
            RestTemplate restTemplate,
            @Value("${cloudflare.api-token:}") String apiToken,
            @Value("${cloudflare.zone-id:}") String zoneId,
            @Value("${cloudflare.custom-hostname-target:}") String targetHostname) {
        this.restTemplate = restTemplate;
        this.apiToken = apiToken;
        this.zoneId = zoneId;
        this.targetHostname = targetHostname;
    }

    public boolean isConfigured() {
        return !apiToken.isBlank() && !zoneId.isBlank() && !targetHostname.isBlank();
    }

    public String getTargetHostname() {
        return targetHostname;
    }

    public CloudflareHostnameResult createOrUpdate(String hostname) {
        if (!isConfigured()) {
            return CloudflareHostnameResult.disabled(hostname, targetHostname);
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                Map.of(
                        "hostname", hostname,
                        "ssl", Map.of("method", "http", "type", "dv", "settings", Map.of()),
                        "custom_metadata", Map.of("product", "rinoimob")
                ),
                headers()
        );

        ResponseEntity<CloudflareCustomHostnameResponse> response = restTemplate.exchange(
                "https://api.cloudflare.com/client/v4/zones/{zoneId}/custom_hostnames",
                HttpMethod.POST,
                request,
                CloudflareCustomHostnameResponse.class,
                zoneId
        );

        CloudflareCustomHostnameResponse body = response.getBody();
        if (body == null || !Boolean.TRUE.equals(body.success) || body.result == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to provision Cloudflare custom hostname");
        }

        return new CloudflareHostnameResult(
                body.result.id,
                body.result.status,
                hostname,
                targetHostname,
                true
        );
    }

    public void delete(String providerId) {
        if (!isConfigured() || providerId == null || providerId.isBlank()) {
            return;
        }

        HttpEntity<Void> request = new HttpEntity<>(headers());
        ResponseEntity<CloudflareCustomHostnameResponse> response = restTemplate.exchange(
                "https://api.cloudflare.com/client/v4/zones/{zoneId}/custom_hostnames/{hostnameId}",
                HttpMethod.DELETE,
                request,
                CloudflareCustomHostnameResponse.class,
                zoneId,
                providerId
        );

        CloudflareCustomHostnameResponse body = response.getBody();
        if (body == null || !Boolean.TRUE.equals(body.success)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to remove Cloudflare custom hostname");
        }
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CloudflareCustomHostnameResponse {
        public Boolean success;
        public List<Map<String, Object>> errors;
        public Result result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Result {
        public String id;
        public String status;
        @JsonProperty("hostname")
        public String hostname;
    }

    public record CloudflareHostnameResult(
            String providerId,
            String status,
            String hostname,
            String targetHostname,
            boolean managedByCloudflare
    ) {
        static CloudflareHostnameResult disabled(String hostname, String targetHostname) {
            return new CloudflareHostnameResult(null, "DISABLED", hostname, targetHostname, false);
        }
    }
}
