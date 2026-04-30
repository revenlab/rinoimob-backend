package com.rinoimob.api.controller;

import com.rinoimob.domain.dto.CreateWhatsappInstanceRequest;
import com.rinoimob.domain.dto.WhatsappInstanceResponse;
import com.rinoimob.domain.dto.WhatsappQrCodeResponse;
import com.rinoimob.service.WhatsappInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/whatsapp/instances")
@RequiredArgsConstructor
public class WhatsappInstanceController {

    private final WhatsappInstanceService service;

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_whatsapp:read')")
    public ResponseEntity<List<WhatsappInstanceResponse>> list() {
        return ResponseEntity.ok(service.listForTenant());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_whatsapp:write')")
    public ResponseEntity<WhatsappInstanceResponse> create(@RequestBody CreateWhatsappInstanceRequest req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @GetMapping("/{id}/qrcode")
    @PreAuthorize("hasAuthority('PERMISSION_whatsapp:read')")
    public ResponseEntity<WhatsappQrCodeResponse> qrCode(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getQrCode(id));
    }

    @GetMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PERMISSION_whatsapp:read')")
    public ResponseEntity<WhatsappInstanceResponse> status(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getStatus(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_whatsapp:write')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
