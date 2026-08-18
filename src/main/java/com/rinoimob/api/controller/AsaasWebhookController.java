package com.rinoimob.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.service.billing.AsaasWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/v1/webhooks/asaas")
@Tag(name = "Webhooks", description = "Inbound Asaas webhooks")
public class AsaasWebhookController {

    private final AsaasWebhookService webhookService;
    private final ObjectMapper objectMapper;
    private final String webhookToken;

    public AsaasWebhookController(AsaasWebhookService webhookService,
                                  ObjectMapper objectMapper,
                                  @Value("${billing.asaas.webhook-token:}") String webhookToken) {
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
        this.webhookToken = webhookToken;
    }

    @PostMapping
    @Operation(summary = "Receive Asaas webhook events")
    public void receive(@RequestBody byte[] rawBody, HttpServletRequest request) throws Exception {
        if (webhookToken == null || webhookToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Asaas webhook token is not configured");
        }
        String receivedToken = request.getHeader("asaas-access-token");
        if (receivedToken == null || !MessageDigest.isEqual(
                receivedToken.getBytes(StandardCharsets.UTF_8), webhookToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Asaas webhook token");
        }
        JsonNode payload = objectMapper.readTree(rawBody);
        webhookService.handleWebhook(payload);
    }
}
