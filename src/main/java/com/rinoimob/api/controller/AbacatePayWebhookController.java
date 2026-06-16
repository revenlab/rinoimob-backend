package com.rinoimob.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.service.billing.AbacatePayWebhookService;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Base64;

@RestController
@RequestMapping("/api/v1/webhooks/abacatepay")
@Tag(name = "Webhooks", description = "Inbound AbacatePay webhooks")
public class AbacatePayWebhookController {

    private final AbacatePayWebhookService webhookService;
    private final ObjectMapper objectMapper;
    private final String webhookQuerySecret;
    private final String webhookSigningSecret;

    public AbacatePayWebhookController(AbacatePayWebhookService webhookService,
                                   ObjectMapper objectMapper,
                                   @Value("${billing.abacatepay.webhook-secret:}") String webhookQuerySecret,
                                   @Value("${billing.abacatepay.webhook-signing-secret:${billing.abacatepay.webhook-secret:}}") String webhookSigningSecret) {
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
        this.webhookQuerySecret = webhookQuerySecret;
        this.webhookSigningSecret = webhookSigningSecret;
    }

    @PostMapping
    @Operation(summary = "Receive AbacatePay webhook events")
    public void receive(@RequestBody byte[] rawBody, HttpServletRequest request) throws Exception {
        if (webhookSigningSecret == null || webhookSigningSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook secret is not configured");
        }

        if (webhookQuerySecret != null && !webhookQuerySecret.isBlank()) {
            String querySecret = request.getParameter("webhookSecret");
            if (querySecret == null || !MessageDigest.isEqual(
                querySecret.getBytes(StandardCharsets.UTF_8),
                webhookQuerySecret.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook secret");
            }
        }

        String signature = firstHeader(request, "X-Webhook-Signature", "x-webhook-signature", "X-Signature");
        if (signature == null || signature.isBlank() || !isValidSignature(rawBody, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }

        JsonNode payload = objectMapper.readTree(rawBody);
        webhookService.handleWebhook(payload);
    }

    private String firstHeader(HttpServletRequest request, String... names) {
        for (String name : names) {
            String value = request.getHeader(name);
            if (value != null && !value.isBlank()) {
            return value.trim();
            }
        }
        return null;
    }

    private boolean isValidSignature(byte[] rawBody, String signature) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSigningSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] expected = mac.doFinal(rawBody);
        byte[] provided = decodeSignature(signature);
        return provided != null && MessageDigest.isEqual(expected, provided);
    }

    private byte[] decodeSignature(String signature) {
        String normalized = signature.trim().replace("\"", "");
        try {
            return Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException ignored) {
            try {
            return HexFormat.of().parseHex(normalized);
            } catch (IllegalArgumentException ignoredToo) {
            return null;
            }
        }
    }
}
