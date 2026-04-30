package com.rinoimob.api.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class EvolutionApiClient {

    @Value("${evolution.api.url:http://localhost:8085}")
    private String baseUrl;

    @Value("${evolution.api.key:changeme-evolution-apikey}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.set("apikey", apiKey);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    /** Creates a new instance in Evolution API with RabbitMQ enabled */
    public void createInstance(String instanceName) {
        Map<String, Object> body = Map.of(
            "instanceName", instanceName,
            "integration", "WHATSAPP-BAILEYS",
            "qrcode", false,
            "groupsIgnore", true,
            "alwaysOnline", false,
            "readMessages", true
        );
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers());
        restTemplate.exchange(baseUrl + "/instance/create", HttpMethod.POST, request, Map.class);
    }

    /** Gets QR code / pairing code for an instance */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getQrCode(String instanceName) {
        HttpEntity<Void> request = new HttpEntity<>(headers());
        ResponseEntity<Map> response = restTemplate.exchange(
            baseUrl + "/instance/connect/" + instanceName,
            HttpMethod.GET, request, Map.class);
        return response.getBody();
    }

    /** Gets current connection state */
    @SuppressWarnings("unchecked")
    public String getConnectionState(String instanceName) {
        try {
            HttpEntity<Void> request = new HttpEntity<>(headers());
            ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/instance/connectionState/" + instanceName,
                HttpMethod.GET, request, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null && body.get("instance") instanceof Map) {
                Map<String, Object> inst = (Map<String, Object>) body.get("instance");
                return (String) inst.get("state"); // open | close | connecting
            }
        } catch (Exception ignored) {}
        return "close";
    }

    /** Sends a plain text message */
    @SuppressWarnings("unchecked")
    public String sendText(String instanceName, String toNumber, String text) {
        Map<String, Object> body = Map.of("number", toNumber, "text", text);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers());
        ResponseEntity<Map> response = restTemplate.exchange(
            baseUrl + "/message/sendText/" + instanceName,
            HttpMethod.POST, request, Map.class);
        Map<String, Object> resp = response.getBody();
        if (resp != null && resp.get("key") instanceof Map) {
            Map<String, Object> key = (Map<String, Object>) resp.get("key");
            return (String) key.get("id");
        }
        return null;
    }

    /** Deletes an instance from Evolution API */
    public void deleteInstance(String instanceName) {
        try {
            HttpEntity<Void> request = new HttpEntity<>(headers());
            restTemplate.exchange(
                baseUrl + "/instance/delete/" + instanceName,
                HttpMethod.DELETE, request, Void.class);
        } catch (Exception ignored) {}
    }
}
