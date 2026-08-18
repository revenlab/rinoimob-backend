package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.*;
import com.rinoimob.service.crm.LeadPipelineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/lead-pipelines")
@RequiredArgsConstructor
public class LeadPipelineController {
    private final LeadPipelineService service;
    @GetMapping @PreAuthorize("hasAnyAuthority('PERMISSION_leads:read','PERMISSION_leads:read_all','PERMISSION_leads:read_own')")
    public List<LeadPipelineResponse> list() { return service.list(UUID.fromString(TenantContext.getTenantId())); }
    @PostMapping @PreAuthorize("hasAuthority('PERMISSION_pipelines:manage')")
    public ResponseEntity<LeadPipelineResponse> create(@Valid @RequestBody CreateLeadPipelineRequest req) { return ResponseEntity.status(HttpStatus.CREATED).body(service.create(UUID.fromString(TenantContext.getTenantId()), req)); }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('PERMISSION_pipelines:manage')")
    public LeadPipelineResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateLeadPipelineRequest req) { return service.update(UUID.fromString(TenantContext.getTenantId()), id, req); }
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('PERMISSION_pipelines:manage')")
    public ResponseEntity<Void> archive(@PathVariable UUID id) { service.archive(UUID.fromString(TenantContext.getTenantId()), id); return ResponseEntity.noContent().build(); }
}
