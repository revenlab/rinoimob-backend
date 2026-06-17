package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.*;
import com.rinoimob.domain.enums.LeadStatus;
import com.rinoimob.service.crm.LeadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns null if user can see/edit ALL leads, or the current userId
     * if user is restricted to their own leads only.
     */
    private UUID resolveScopedUserId(Authentication auth, HttpServletRequest request, boolean readOperation) {
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        boolean hasAll = authorities.stream().anyMatch(a ->
                a.getAuthority().equals(readOperation ? "PERMISSION_leads:read_all" : "PERMISSION_leads:write_all") ||
                a.getAuthority().equals(readOperation ? "PERMISSION_leads:read" : "PERMISSION_leads:write"));
        if (hasAll) return null;
        return (UUID) request.getAttribute("userId");
    }

    // ── Endpoints ─────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('PERMISSION_leads:read','PERMISSION_leads:read_all','PERMISSION_leads:read_own')")
    public LeadStatsResponse stats(Authentication auth, HttpServletRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID scopedUserId = resolveScopedUserId(auth, request, true);
        return leadService.getStats(tenantId, scopedUserId);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERMISSION_leads:read','PERMISSION_leads:read_all','PERMISSION_leads:read_own')")
    public ResponseEntity<Page<LeadResponse>> list(
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth, HttpServletRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID scopedUserId = resolveScopedUserId(auth, request, true);
        return ResponseEntity.ok(leadService.list(tenantId, scopedUserId, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERMISSION_leads:read','PERMISSION_leads:read_all','PERMISSION_leads:read_own')")
    public ResponseEntity<LeadResponse> get(@PathVariable UUID id, Authentication auth, HttpServletRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID scopedUserId = resolveScopedUserId(auth, request, true);
        return ResponseEntity.ok(leadService.get(tenantId, id, scopedUserId));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('PERMISSION_leads:write','PERMISSION_leads:write_all','PERMISSION_leads:write_own')")
    public ResponseEntity<LeadResponse> create(
            @Valid @RequestBody CreateLeadRequest request,
            Authentication auth,
            HttpServletRequest httpRequest) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID scopedUserId = resolveScopedUserId(auth, httpRequest, false);
        return ResponseEntity.status(HttpStatus.CREATED).body(leadService.create(tenantId, scopedUserId, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERMISSION_leads:write','PERMISSION_leads:write_all','PERMISSION_leads:write_own')")
    public ResponseEntity<LeadResponse> update(
            @PathVariable UUID id,
            @RequestBody UpdateLeadRequest req,
            Authentication auth, HttpServletRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID scopedUserId = resolveScopedUserId(auth, request, false);
        return ResponseEntity.ok(leadService.update(tenantId, id, scopedUserId, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERMISSION_leads:write','PERMISSION_leads:write_all','PERMISSION_leads:write_own')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth, HttpServletRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID scopedUserId = resolveScopedUserId(auth, request, false);
        leadService.delete(tenantId, id, scopedUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize("hasAnyAuthority('PERMISSION_leads:write','PERMISSION_leads:write_all','PERMISSION_leads:write_own')")
    public ResponseEntity<LeadNoteResponse> addNote(
            @PathVariable UUID id,
            @Valid @RequestBody LeadNoteRequest req,
            Authentication auth, HttpServletRequest httpRequest) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID userId = (UUID) httpRequest.getAttribute("userId");
        UUID scopedUserId = resolveScopedUserId(auth, httpRequest, false);
        return ResponseEntity.status(HttpStatus.CREATED).body(leadService.addNote(tenantId, id, userId, scopedUserId, req));
    }

    @GetMapping("/{id}/events")
    @PreAuthorize("hasAnyAuthority('PERMISSION_leads:read','PERMISSION_leads:read_all','PERMISSION_leads:read_own')")
    public ResponseEntity<List<LeadEventResponse>> getEvents(@PathVariable UUID id, Authentication auth, HttpServletRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID scopedUserId = resolveScopedUserId(auth, request, true);
        return ResponseEntity.ok(leadService.getEvents(tenantId, id, scopedUserId));
    }

    @GetMapping("/{id}/properties")
    @PreAuthorize("hasAnyAuthority('PERMISSION_leads:read','PERMISSION_leads:read_all','PERMISSION_leads:read_own')")
    public ResponseEntity<List<LeadPropertyResponse>> getProperties(@PathVariable UUID id, Authentication auth, HttpServletRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID scopedUserId = resolveScopedUserId(auth, request, true);
        return ResponseEntity.ok(leadService.getProperties(tenantId, id, scopedUserId));
    }

    @PostMapping("/{id}/properties")
    @PreAuthorize("hasAnyAuthority('PERMISSION_leads:write','PERMISSION_leads:write_all','PERMISSION_leads:write_own')")
    public ResponseEntity<LeadPropertyResponse> addProperty(
            @PathVariable UUID id,
            @Valid @RequestBody AddLeadPropertyRequest req,
            Authentication auth, HttpServletRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID scopedUserId = resolveScopedUserId(auth, request, false);
        return ResponseEntity.status(HttpStatus.CREATED).body(leadService.addProperty(tenantId, id, scopedUserId, req));
    }

    @PatchMapping("/{id}/properties/{linkId}")
    @PreAuthorize("hasAnyAuthority('PERMISSION_leads:write','PERMISSION_leads:write_all','PERMISSION_leads:write_own')")
    public ResponseEntity<LeadPropertyResponse> updatePropertyInterest(
            @PathVariable UUID id,
            @PathVariable UUID linkId,
            @Valid @RequestBody UpdateLeadPropertyRequest req,
            Authentication auth, HttpServletRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID scopedUserId = resolveScopedUserId(auth, request, false);
        return ResponseEntity.ok(leadService.updatePropertyInterest(tenantId, id, linkId, scopedUserId, req));
    }

    @DeleteMapping("/{id}/properties/{linkId}")
    @PreAuthorize("hasAnyAuthority('PERMISSION_leads:write','PERMISSION_leads:write_all','PERMISSION_leads:write_own')")
    public ResponseEntity<Void> removeProperty(
            @PathVariable UUID id,
            @PathVariable UUID linkId,
            Authentication auth, HttpServletRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        UUID scopedUserId = resolveScopedUserId(auth, request, false);
        leadService.removeProperty(tenantId, id, linkId, scopedUserId);
        return ResponseEntity.noContent().build();
    }
}
