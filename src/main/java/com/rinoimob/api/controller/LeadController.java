package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.*;
import com.rinoimob.domain.enums.LeadStatus;
import com.rinoimob.service.LeadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    @GetMapping
    public ResponseEntity<Page<LeadResponse>> list(
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(leadService.list(tenantId, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeadResponse> get(@PathVariable UUID id) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(leadService.get(tenantId, id));
    }

    @PostMapping
    public ResponseEntity<LeadResponse> create(
            @Valid @RequestBody CreateLeadRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(leadService.create(tenantId, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LeadResponse> update(
            @PathVariable UUID id,
            @RequestBody UpdateLeadRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(leadService.update(tenantId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        leadService.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<LeadNoteResponse> addNote(
            @PathVariable UUID id,
            @Valid @RequestBody LeadNoteRequest request,
            HttpServletRequest httpRequest) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID userId = (UUID) httpRequest.getAttribute("userId");
        return ResponseEntity.status(HttpStatus.CREATED).body(leadService.addNote(tenantId, id, userId, request));
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<List<LeadEventResponse>> getEvents(@PathVariable UUID id) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(leadService.getEvents(tenantId, id));
    }
}
