package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.CreateLeadPoolRequest;
import com.rinoimob.domain.dto.LeadPoolResponse;
import com.rinoimob.domain.dto.UpdateLeadPoolRequest;
import com.rinoimob.service.crm.LeadPoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Lead Pools", description = "Tenant-scoped lead pool management")
@RequiredArgsConstructor
public class LeadPoolController {

    private final LeadPoolService leadPoolService;

    @GetMapping
    @Operation(summary = "List lead pools for the current tenant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lead pools retrieved"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @PreAuthorize("hasAnyAuthority('PERMISSION_leads:read','PERMISSION_leads:read_all')")
    public ResponseEntity<List<LeadPoolResponse>> list() {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(leadPoolService.list(tenantId));
    }

    @PostMapping
    @Operation(summary = "Create a lead pool for the current tenant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Lead pool created"),
            @ApiResponse(responseCode = "400", description = "Invalid lead pool data"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @PreAuthorize("hasAnyAuthority('PERMISSION_leads:write','PERMISSION_leads:write_all')")
    public ResponseEntity<LeadPoolResponse> create(@Valid @RequestBody CreateLeadPoolRequest req) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(leadPoolService.create(tenantId, req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a lead pool owned by the current tenant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lead pool updated"),
            @ApiResponse(responseCode = "400", description = "Invalid lead pool data"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @ApiResponse(responseCode = "404", description = "Lead pool not found")
    })
    @PreAuthorize("hasAnyAuthority('PERMISSION_leads:write','PERMISSION_leads:write_all')")
    public ResponseEntity<LeadPoolResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateLeadPoolRequest req) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(leadPoolService.update(tenantId, id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a lead pool owned by the current tenant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Lead pool deleted"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @ApiResponse(responseCode = "404", description = "Lead pool not found")
    })
    @PreAuthorize("hasAnyAuthority('PERMISSION_leads:write','PERMISSION_leads:write_all')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        leadPoolService.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
