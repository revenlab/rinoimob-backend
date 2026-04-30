package com.rinoimob.api.controller;

import com.rinoimob.domain.dto.SendWhatsappMessageRequest;
import com.rinoimob.domain.dto.WhatsappMessageResponse;
import com.rinoimob.service.WhatsappMessageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/whatsapp/leads")
@RequiredArgsConstructor
public class WhatsappMessageController {

    private final WhatsappMessageService service;

    @GetMapping("/{leadId}/messages")
    public ResponseEntity<List<WhatsappMessageResponse>> getMessages(@PathVariable UUID leadId) {
        return ResponseEntity.ok(service.getMessagesForLead(leadId));
    }

    @PostMapping("/{leadId}/messages")
    public ResponseEntity<WhatsappMessageResponse> send(
            @PathVariable UUID leadId,
            @RequestBody SendWhatsappMessageRequest req,
            HttpServletRequest httpRequest) {
        UUID userId = (UUID) httpRequest.getAttribute("userId");
        return ResponseEntity.status(201).body(service.send(leadId, req, userId));
    }
}
