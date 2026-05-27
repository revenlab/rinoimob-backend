package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.TenantWebsiteConfigResponse;
import com.rinoimob.domain.dto.UpdateTenantWebsiteConfigRequest;
import com.rinoimob.domain.dto.tenant.UpdateTenantDomainRequest;
import com.rinoimob.domain.dto.tenant.TenantDomainResponse;
import com.rinoimob.service.TenantWebsiteConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/website-config")
@RequiredArgsConstructor
public class TenantWebsiteConfigController {

    private final TenantWebsiteConfigService tenantWebsiteConfigService;

    @GetMapping
    public ResponseEntity<TenantWebsiteConfigResponse> getConfig() {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(tenantWebsiteConfigService.getConfig(tenantId));
    }

    @PutMapping
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    public ResponseEntity<TenantWebsiteConfigResponse> updateConfig(
            @RequestBody UpdateTenantWebsiteConfigRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(tenantWebsiteConfigService.updateConfig(tenantId, request));
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    public ResponseEntity<TenantWebsiteConfigResponse> uploadLogo(@RequestPart("file") MultipartFile file) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantWebsiteConfigService.uploadLogo(tenantId, file));
    }

    @DeleteMapping("/logo")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    public ResponseEntity<TenantWebsiteConfigResponse> deleteLogo() {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(tenantWebsiteConfigService.deleteLogo(tenantId));
    }

    @PostMapping(value = "/favicon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    public ResponseEntity<TenantWebsiteConfigResponse> uploadFavicon(@RequestPart("file") MultipartFile file) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantWebsiteConfigService.uploadFavicon(tenantId, file));
    }

    @DeleteMapping("/favicon")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    public ResponseEntity<TenantWebsiteConfigResponse> deleteFavicon() {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(tenantWebsiteConfigService.deleteFavicon(tenantId));
    }

    @PostMapping(value = "/hero-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    public ResponseEntity<TenantWebsiteConfigResponse> uploadHeroImage(@RequestPart("file") MultipartFile file) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantWebsiteConfigService.uploadHeroImage(tenantId, file));
    }

    @DeleteMapping("/hero-image")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    public ResponseEntity<TenantWebsiteConfigResponse> deleteHeroImage() {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(tenantWebsiteConfigService.deleteHeroImage(tenantId));
    }

    @PostMapping(value = "/about-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    public ResponseEntity<TenantWebsiteConfigResponse> uploadAboutImage(@RequestPart("file") MultipartFile file) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantWebsiteConfigService.uploadAboutImage(tenantId, file));
    }

    @DeleteMapping("/about-image")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    public ResponseEntity<TenantWebsiteConfigResponse> deleteAboutImage() {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(tenantWebsiteConfigService.deleteAboutImage(tenantId));
    }

    @GetMapping("/domain")
    public ResponseEntity<TenantDomainResponse> getCustomDomain() {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        TenantWebsiteConfigResponse config = tenantWebsiteConfigService.getConfig(tenantId);
        return ResponseEntity.ok(new TenantDomainResponse(config.customDomain()));
    }

    @PutMapping("/domain")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    public ResponseEntity<TenantDomainResponse> updateCustomDomain(
            @RequestBody UpdateTenantDomainRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        TenantWebsiteConfigResponse config = tenantWebsiteConfigService.updateCustomDomain(tenantId, request.customDomain());
        return ResponseEntity.ok(new TenantDomainResponse(config.customDomain()));
    }
}
