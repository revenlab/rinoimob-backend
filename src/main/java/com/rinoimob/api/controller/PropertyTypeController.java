package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.PropertyTypeResponse;
import com.rinoimob.domain.dto.UpdatePropertyTypeRequest;
import com.rinoimob.domain.enums.PropertyType;
import com.rinoimob.service.imoveis.PropertyTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/property-types")
@RequiredArgsConstructor
public class PropertyTypeController {

    private final PropertyTypeService propertyTypeService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_properties:read')")
    public List<PropertyTypeResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return activeOnly ? propertyTypeService.listActive(tenantId) : propertyTypeService.list(tenantId);
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('PERMISSION_properties:write')")
    public PropertyTypeResponse update(
            @PathVariable PropertyType code,
            @Valid @RequestBody UpdatePropertyTypeRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return propertyTypeService.update(tenantId, code, request);
    }

    @PostMapping(value = "/{code}/cover-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERMISSION_properties:write')")
    public ResponseEntity<PropertyTypeResponse> uploadCoverImage(
            @PathVariable PropertyType code,
            @RequestPart("file") MultipartFile file) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(propertyTypeService.uploadCoverImage(tenantId, code, file));
    }

    @DeleteMapping("/{code}/cover-image")
    @PreAuthorize("hasAuthority('PERMISSION_properties:write')")
    public PropertyTypeResponse deleteCoverImage(@PathVariable PropertyType code) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return propertyTypeService.deleteCoverImage(tenantId, code);
    }
}
