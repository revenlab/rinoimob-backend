package com.rinoimob.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.api.client.MetaLeadAdsClient;
import com.rinoimob.service.integration.IntegrationInboxService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

@RestController
@RequestMapping("/api/v1/webhooks/integrations")
@RequiredArgsConstructor
public class IntegrationWebhookController {
    private final IntegrationInboxService inboxService;
    private final MetaLeadAdsClient metaClient;
    private final ObjectMapper objectMapper;

    @Value("${integrations.meta.verify-token:}")
    private String metaVerifyToken;

    @Value("${integrations.grupo-olx.secret-key:}")
    private String grupoOlxSecretKey;

    @GetMapping("/meta")
    @Operation(summary = "Verify the Meta Lead Ads webhook")
    public ResponseEntity<String> verifyMeta(@RequestParam(name = "hub.mode") String mode,
                                              @RequestParam(name = "hub.verify_token") String verifyToken,
                                              @RequestParam(name = "hub.challenge") String challenge) {
        if (!"subscribe".equals(mode) || metaVerifyToken.isBlank() || !constantEquals(metaVerifyToken, verifyToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid Meta verification token");
        }
        return ResponseEntity.ok(challenge);
    }

    @PostMapping("/meta")
    @Operation(summary = "Receive Meta Lead Ads events")
    public ResponseEntity<Void> receiveMeta(@RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
                                             @RequestBody byte[] body) {
        validateMetaSignature(signature, body);
        try {
            inboxService.receiveMeta(objectMapper.readTree(body));
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Meta webhook payload");
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/grupo-olx/{inboundKey}")
    @Operation(summary = "Receive Grupo OLX, ZAP and Viva Real leads")
    public ResponseEntity<Void> receiveGrupoOlx(@PathVariable String inboundKey,
                                                 @RequestHeader(name = "Authorization", required = false) String authorization,
                                                 @RequestBody JsonNode payload) {
        validateGrupoOlxAuthorization(authorization);
        inboxService.receiveGrupoOlx(inboundKey, payload);
        return ResponseEntity.accepted().build();
    }

    private void validateMetaSignature(String signature, byte[] body) {
        if (signature == null || !signature.startsWith("sha256=") || metaClient.appSecret().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Meta signature");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(metaClient.appSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
            if (!constantEquals(expected, signature)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Meta signature");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not verify Meta signature");
        }
    }

    private void validateGrupoOlxAuthorization(String authorization) {
        if (grupoOlxSecretKey.isBlank() || authorization == null || !authorization.startsWith("Basic ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Grupo OLX credentials");
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            String receivedSecret = separator >= 0 ? decoded.substring(separator + 1) : "";
            if (!constantEquals(grupoOlxSecretKey, receivedSecret)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Grupo OLX credentials");
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Grupo OLX credentials");
        }
    }

    private boolean constantEquals(String expected, String received) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), received.getBytes(StandardCharsets.UTF_8));
    }
}
