package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.LeadResponse;
import com.rinoimob.domain.dto.PublicCreateLeadRequest;
import com.rinoimob.domain.dto.property.PropertySummaryResponse;
import com.rinoimob.domain.dto.property.PropertyResponse;
import com.rinoimob.domain.entity.Lead;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.enums.LeadStatus;
import com.rinoimob.domain.enums.PropertyOperation;
import com.rinoimob.domain.enums.PropertyStatus;
import com.rinoimob.domain.enums.PropertyType;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.service.LeadService;
import com.rinoimob.service.PropertyService;
import com.rinoimob.domain.dto.CreateLeadRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicController {

    private final TenantRepository tenantRepository;
    private final PropertyService propertyService;
    private final LeadService leadService;

    @GetMapping("/properties")
    public ResponseEntity<Page<PropertySummaryResponse>> listProperties(
            @RequestHeader("X-Tenant-Slug") String tenantSlug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) PropertyOperation operation,
            @RequestParam(required = false) PropertyType propertyType) {
        UUID tenantId = resolveTenant(tenantSlug);
        TenantContext.setTenantId(tenantId.toString());
        try {
            Pageable pageable = PageRequest.of(page, size);
            return ResponseEntity.ok(propertyService.listProperties(
                    PropertyStatus.ACTIVE, operation, propertyType,
                    null, null, null, null, pageable));
        } finally {
            TenantContext.clear();
        }
    }

    @GetMapping("/properties/{id}")
    public ResponseEntity<PropertyResponse> getProperty(
            @RequestHeader("X-Tenant-Slug") String tenantSlug,
            @PathVariable UUID id) {
        UUID tenantId = resolveTenant(tenantSlug);
        TenantContext.setTenantId(tenantId.toString());
        try {
            return ResponseEntity.ok(propertyService.getProperty(id));
        } finally {
            TenantContext.clear();
        }
    }

    @PostMapping("/leads")
    public ResponseEntity<Map<String, String>> createLead(
            @RequestHeader("X-Tenant-Slug") String tenantSlug,
            @Valid @RequestBody PublicCreateLeadRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        UUID tenantId = resolveTenant(tenantSlug);
        CreateLeadRequest leadReq = new CreateLeadRequest(
                request.name(), request.email(), request.phone(),
                request.message(), request.propertyId(), "PORTAL");
        leadService.create(tenantId, leadReq);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Lead received successfully"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private UUID resolveTenant(String slug) {
        return tenantRepository.findBySubdomain(slug)
                .map(Tenant::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }
}
