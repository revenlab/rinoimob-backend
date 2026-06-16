package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.CreateLeadPoolRequest;
import com.rinoimob.domain.dto.LeadPoolResponse;
import com.rinoimob.domain.dto.UpdateLeadPoolRequest;
import com.rinoimob.service.crm.LeadPoolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lead-pools")
@RequiredArgsConstructor
public class LeadPoolController {

    private final LeadPoolService leadPoolService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERMISSION_leads:read','PERMISSION_leads:read_all')")
    public ResponseEntity<List<LeadPoolResponse>> list() {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(leadPoolService.list(tenantId));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('PERMISSION_leads:write','PERMISSION_leads:write_all')")
    public ResponseEntity<LeadPoolResponse> create(@Valid @RequestBody CreateLeadPoolRequest req) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(leadPoolService.create(tenantId, req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERMISSION_leads:write','PERMISSION_leads:write_all')")
    public ResponseEntity<LeadPoolResponse> update(@PathVariable UUID id, @RequestBody UpdateLeadPoolRequest req) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(leadPoolService.update(tenantId, id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERMISSION_leads:write','PERMISSION_leads:write_all')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        leadPoolService.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
