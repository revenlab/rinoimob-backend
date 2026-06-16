package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.PublicCreateLeadRequest;
import com.rinoimob.domain.dto.property.PropertySummaryResponse;
import com.rinoimob.domain.dto.property.PropertyResponse;
import com.rinoimob.domain.dto.TenantWebsiteConfigResponse;
import com.rinoimob.domain.dto.blog.PublicBlogPostResponse;
import com.rinoimob.domain.dto.blog.PublicBlogPostSummaryResponse;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.enums.PropertyOperation;
import com.rinoimob.domain.enums.PropertyStatus;
import com.rinoimob.domain.enums.PropertyType;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.service.website.BlogPostService;
import com.rinoimob.service.crm.LeadService;
import com.rinoimob.service.imoveis.PropertyService;
import com.rinoimob.service.website.TenantWebsiteConfigService;
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

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicController {

    private final TenantRepository tenantRepository;
    private final PropertyService propertyService;
    private final LeadService leadService;
    private final TenantWebsiteConfigService tenantWebsiteConfigService;
    private final BlogPostService blogPostService;

    @GetMapping("/properties")
    public ResponseEntity<Page<PropertySummaryResponse>> listProperties(
            @RequestHeader("X-Tenant-Slug") String tenantSlug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) PropertyOperation operation,
            @RequestParam(required = false) PropertyType propertyType,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer bedrooms,
            @RequestParam(required = false) String city,
            @RequestParam(required = false, name = "q") String queryText,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) BigDecimal radiusKm) {
        UUID tenantId = resolveTenant(tenantSlug);
        TenantContext.setTenantId(tenantId.toString());
        try {
            Pageable pageable = PageRequest.of(page, size);
            return ResponseEntity.ok(propertyService.listProperties(
                    PropertyStatus.ACTIVE, operation, propertyType,
                    categorySlug, minPrice, maxPrice, bedrooms, city, queryText,
                    latitude, longitude, radiusKm, pageable));
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

    @GetMapping("/config")
    public ResponseEntity<TenantWebsiteConfigResponse> getWebsiteConfig(
            @RequestHeader("X-Tenant-Slug") String tenantSlug) {
        UUID tenantId = resolveTenant(tenantSlug);
        TenantContext.setTenantId(tenantId.toString());
        try {
            return ResponseEntity.ok(tenantWebsiteConfigService.getConfig(tenantId));
        } finally {
            TenantContext.clear();
        }
    }

    @GetMapping("/blog-posts")
    public ResponseEntity<Page<PublicBlogPostSummaryResponse>> listBlogPosts(
            @RequestHeader("X-Tenant-Slug") String tenantSlug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = resolveTenant(tenantSlug);
        TenantContext.setTenantId(tenantId.toString());
        try {
            return ResponseEntity.ok(blogPostService.listPublic(tenantId, page, size));
        } finally {
            TenantContext.clear();
        }
    }

    @GetMapping("/blog-posts/{slug}")
    public ResponseEntity<PublicBlogPostResponse> getBlogPost(
            @RequestHeader("X-Tenant-Slug") String tenantSlug,
            @PathVariable String slug) {
        UUID tenantId = resolveTenant(tenantSlug);
        TenantContext.setTenantId(tenantId.toString());
        try {
            return ResponseEntity.ok(blogPostService.getPublicBySlug(tenantId, slug));
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
        TenantContext.setTenantId(tenantId.toString());
        try {
            CreateLeadRequest leadReq = new CreateLeadRequest(
                    request.name(),
                    request.email(),
                    request.phone(),
                    request.message(),
                    request.propertyId(),
                    normalizePublicLeadSource(request.source())
            );
            leadService.create(tenantId, leadReq);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Lead received successfully"));
        } finally {
            TenantContext.clear();
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private UUID resolveTenant(String slug) {
        return tenantRepository.findBySubdomain(slug)
                .map(Tenant::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private String normalizePublicLeadSource(String source) {
        if (source == null || source.isBlank()) {
            return "PORTAL";
        }

        String normalized = source.trim().toUpperCase().replaceAll("[^A-Z0-9_\\-]", "_");
        if (!normalized.startsWith("PORTAL")) {
            return "PORTAL";
        }

        if (normalized.length() > 50) {
            return normalized.substring(0, 50);
        }

        return normalized;
    }
}
