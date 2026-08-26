package com.rinoimob.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.rinoimob.service.billing.AsaasWebhookInboxService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    private final AsaasWebhookInboxService webhookInboxService;
    private final ObjectMapper objectMapper;
    private final String webhookToken;

    public AsaasWebhookController(AsaasWebhookInboxService webhookInboxService,
                                  ObjectMapper objectMapper,
                                  @Value("${billing.asaas.webhook-token:}") String webhookToken) {
        this.webhookInboxService = webhookInboxService;
        this.objectMapper = objectMapper;
        this.webhookToken = webhookToken;
    }

    @PostMapping
    @Operation(summary = "Receive Asaas webhook events")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Event persisted for asynchronous processing"),
            @ApiResponse(responseCode = "401", description = "Invalid webhook token")
    })
    public ResponseEntity<Void> receive(@RequestBody byte[] rawBody, HttpServletRequest request) throws Exception {
        if (webhookToken == null || webhookToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Asaas webhook token is not configured");
        }
        String receivedToken = request.getHeader("asaas-access-token");
        if (receivedToken == null || !MessageDigest.isEqual(
                receivedToken.getBytes(StandardCharsets.UTF_8), webhookToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Asaas webhook token");
        }
        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Asaas webhook JSON", exception);
        }
        if (payload == null || !payload.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Asaas webhook payload");
        }
        webhookInboxService.receive(payload);
        return ResponseEntity.accepted().build();
    }
}
