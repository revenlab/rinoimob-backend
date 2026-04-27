package com.rinoimob.api.controller;

import com.rinoimob.domain.dto.property.*;
import com.rinoimob.domain.enums.PropertyOperation;
import com.rinoimob.domain.enums.PropertyStatus;
import com.rinoimob.domain.enums.PropertyType;
import com.rinoimob.service.PropertyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;

    @GetMapping
    public ResponseEntity<Page<PropertySummaryResponse>> list(
            @RequestParam(required = false) PropertyStatus status,
            @RequestParam(required = false) PropertyOperation operation,
            @RequestParam(required = false) PropertyType propertyType,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer bedrooms,
            @RequestParam(required = false) String city,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(propertyService.listProperties(
                status, operation, propertyType, minPrice, maxPrice, bedrooms, city, pageable));
    }

    @PostMapping
    public ResponseEntity<PropertyResponse> create(@Valid @RequestBody CreatePropertyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(propertyService.createProperty(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PropertyResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(propertyService.getProperty(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PropertyResponse> update(
            @PathVariable UUID id,
            @RequestBody UpdatePropertyRequest request) {
        return ResponseEntity.ok(propertyService.updateProperty(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        propertyService.deleteProperty(id);
        return ResponseEntity.noContent().build();
    }

    // ── Photos ────────────────────────────────────────────────────────────────

    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PropertyPhotoResponse> uploadPhoto(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "altText", required = false) String altText) {
        return ResponseEntity.status(HttpStatus.CREATED).body(propertyService.addPhoto(id, file, altText));
    }

    @PatchMapping("/{id}/photos/{photoId}/cover")
    public ResponseEntity<Void> setCover(@PathVariable UUID id, @PathVariable UUID photoId) {
        propertyService.setCoverPhoto(id, photoId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    public ResponseEntity<Void> deletePhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        propertyService.deletePhoto(id, photoId);
        return ResponseEntity.noContent().build();
    }

    // ── Floor Plans ───────────────────────────────────────────────────────────

    @PostMapping("/{id}/floor-plans")
    public ResponseEntity<FloorPlanResponse> addFloorPlan(
            @PathVariable UUID id,
            @Valid @RequestBody CreateFloorPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(propertyService.addFloorPlan(id, request));
    }

    @DeleteMapping("/{id}/floor-plans/{planId}")
    public ResponseEntity<Void> deleteFloorPlan(@PathVariable UUID id, @PathVariable UUID planId) {
        propertyService.deleteFloorPlan(id, planId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/floor-plans/{planId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FloorPlanPhotoResponse> uploadFloorPlanPhoto(
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(propertyService.addFloorPlanPhoto(id, planId, file));
    }
}
