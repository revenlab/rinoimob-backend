package com.rinoimob.api.controller;

import com.rinoimob.domain.dto.property.*;
import com.rinoimob.domain.enums.PropertyOperation;
import com.rinoimob.domain.enums.PropertyStatus;
import com.rinoimob.domain.enums.PropertyType;
import com.rinoimob.service.imoveis.PropertyService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.Map;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;

    @Operation(summary = "Consultar permissões de cadastro e oferta de imóveis")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Permissões consultadas"),
            @ApiResponse(responseCode = "403", description = "Acesso negado")})
    @GetMapping("/access")
    @PreAuthorize("hasAuthority('PERMISSION_properties:read')")
    public ResponseEntity<Map<String, Boolean>> access() {
        return ResponseEntity.ok(Map.of("canCreate", propertyService.canCreateProperty(),
                "canOfferToAll", propertyService.canOfferToAllBrokers()));
    }

    @Operation(summary = "Listar nomes dos corretores disponíveis para oferta de imóveis")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Corretores do tenant"),
            @ApiResponse(responseCode = "403", description = "Acesso negado")})
    @GetMapping("/brokers")
    @PreAuthorize("hasAnyAuthority('PERMISSION_properties:write','PERMISSION_properties:write_all','PERMISSION_properties:write_own')")
    public ResponseEntity<List<Map<String, Object>>> offeringBrokers() {
        return ResponseEntity.ok(propertyService.listOfferingBrokers());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_properties:read')")
    public ResponseEntity<Page<PropertyListItemResponse>> list(
            @RequestParam(required = false) PropertyStatus status,
            @RequestParam(required = false) PropertyOperation operation,
            @RequestParam(required = false) PropertyType propertyType,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer bedrooms,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Boolean featured,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(propertyService.listManagedProperties(
                status, operation, propertyType, categorySlug, minPrice, maxPrice, bedrooms, city, featured, pageable));
    }

    @Operation(summary = "Cadastrar imóvel e definir os corretores que podem ofertá-lo")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('PERMISSION_properties:write','PERMISSION_properties:write_all','PERMISSION_properties:write_own')")
    public ResponseEntity<PropertyResponse> create(@Valid @RequestBody CreatePropertyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(propertyService.createProperty(request));
    }

    @Operation(summary = "Consultar a permissão de edição do imóvel para o usuário atual")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Permissão consultada"),
            @ApiResponse(responseCode = "403", description = "Acesso negado"),
            @ApiResponse(responseCode = "404", description = "Imóvel não encontrado neste tenant")})
    @GetMapping("/{id}/access")
    @PreAuthorize("hasAuthority('PERMISSION_properties:read')")
    public ResponseEntity<Map<String, Boolean>> access(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("canEdit", propertyService.canEditProperty(id)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_properties:read')")
    public ResponseEntity<PropertyResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(propertyService.getProperty(id));
    }

    @Operation(summary = "Atualizar imóvel conforme permissão própria ou geral")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERMISSION_properties:write','PERMISSION_properties:write_all','PERMISSION_properties:write_own')")
    public ResponseEntity<PropertyResponse> update(
            @PathVariable UUID id,
            @RequestBody UpdatePropertyRequest request) {
        return ResponseEntity.ok(propertyService.updateProperty(id, request));
    }

    @Operation(summary = "Excluir imóvel conforme permissão própria ou geral")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERMISSION_properties:write','PERMISSION_properties:write_all','PERMISSION_properties:write_own')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        propertyService.deleteProperty(id);
        return ResponseEntity.noContent().build();
    }

    // ── Photos ────────────────────────────────────────────────────────────────

    @Operation(summary = "Enviar foto de imóvel")
    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('PERMISSION_properties:write','PERMISSION_properties:write_all','PERMISSION_properties:write_own')")
    public ResponseEntity<PropertyPhotoResponse> uploadPhoto(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "altText", required = false) String altText) {
        return ResponseEntity.status(HttpStatus.CREATED).body(propertyService.addPhoto(id, file, altText));
    }

    @PatchMapping("/{id}/photos/{photoId}/cover")
    @PreAuthorize("hasAnyAuthority('PERMISSION_properties:write','PERMISSION_properties:write_all','PERMISSION_properties:write_own')")
    public ResponseEntity<Void> setCover(@PathVariable UUID id, @PathVariable UUID photoId) {
        propertyService.setCoverPhoto(id, photoId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remover foto do imóvel")
    @DeleteMapping("/{id}/photos/{photoId}")
    @PreAuthorize("hasAnyAuthority('PERMISSION_properties:write','PERMISSION_properties:write_all','PERMISSION_properties:write_own')")
    public ResponseEntity<Void> deletePhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        propertyService.deletePhoto(id, photoId);
        return ResponseEntity.noContent().build();
    }

    // ── Videos ───────────────────────────────────────────────────────────────

    @Operation(summary = "Enviar vídeo do imóvel")
    @PostMapping(value = "/{id}/videos/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('PERMISSION_properties:write','PERMISSION_properties:write_all','PERMISSION_properties:write_own')")
    public ResponseEntity<PropertyVideoResponse> uploadVideo(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "title", required = false) String title) {
        return ResponseEntity.status(HttpStatus.CREATED).body(propertyService.addUploadedVideo(id, file, title));
    }

    @Operation(summary = "Adicionar vídeo do YouTube ao imóvel")
    @PostMapping("/{id}/videos/youtube")
    @PreAuthorize("hasAnyAuthority('PERMISSION_properties:write','PERMISSION_properties:write_all','PERMISSION_properties:write_own')")
    public ResponseEntity<PropertyVideoResponse> addYoutubeVideo(
            @PathVariable UUID id,
            @Valid @RequestBody CreateYoutubeVideoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(propertyService.addYoutubeVideo(id, request));
    }

    @Operation(summary = "Remover vídeo do imóvel")
    @DeleteMapping("/{id}/videos/{videoId}")
    @PreAuthorize("hasAnyAuthority('PERMISSION_properties:write','PERMISSION_properties:write_all','PERMISSION_properties:write_own')")
    public ResponseEntity<Void> deleteVideo(@PathVariable UUID id, @PathVariable UUID videoId) {
        propertyService.deleteVideo(id, videoId);
        return ResponseEntity.noContent().build();
    }

    // ── Floor Plans ───────────────────────────────────────────────────────────

    @Operation(summary = "Adicionar planta do imóvel")
    @PostMapping("/{id}/floor-plans")
    @PreAuthorize("hasAnyAuthority('PERMISSION_properties:write','PERMISSION_properties:write_all','PERMISSION_properties:write_own')")
    public ResponseEntity<FloorPlanResponse> addFloorPlan(
            @PathVariable UUID id,
            @Valid @RequestBody CreateFloorPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(propertyService.addFloorPlan(id, request));
    }

    @Operation(summary = "Atualizar planta do imóvel")
    @PutMapping("/{id}/floor-plans/{planId}")
    @PreAuthorize("hasAnyAuthority('PERMISSION_properties:write','PERMISSION_properties:write_all','PERMISSION_properties:write_own')")
    public ResponseEntity<FloorPlanResponse> updateFloorPlan(
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @RequestBody UpdateFloorPlanRequest request) {
        return ResponseEntity.ok(propertyService.updateFloorPlan(id, planId, request));
    }

    @Operation(summary = "Remover planta do imóvel")
    @DeleteMapping("/{id}/floor-plans/{planId}")
    @PreAuthorize("hasAnyAuthority('PERMISSION_properties:write','PERMISSION_properties:write_all','PERMISSION_properties:write_own')")
    public ResponseEntity<Void> deleteFloorPlan(@PathVariable UUID id, @PathVariable UUID planId) {
        propertyService.deleteFloorPlan(id, planId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/floor-plans/{planId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('PERMISSION_properties:write','PERMISSION_properties:write_all','PERMISSION_properties:write_own')")
    public ResponseEntity<FloorPlanPhotoResponse> uploadFloorPlanPhoto(
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(propertyService.addFloorPlanPhoto(id, planId, file));
    }

    @Operation(summary = "Definir capa da planta do imóvel")
    @PatchMapping("/{id}/floor-plans/{planId}/photos/{photoId}/cover")
    @PreAuthorize("hasAnyAuthority('PERMISSION_properties:write','PERMISSION_properties:write_all','PERMISSION_properties:write_own')")
    public ResponseEntity<Void> setFloorPlanPhotoCover(
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @PathVariable UUID photoId) {
        propertyService.setFloorPlanPhotoCover(id, planId, photoId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remover foto da planta do imóvel")
    @DeleteMapping("/{id}/floor-plans/{planId}/photos/{photoId}")
    @PreAuthorize("hasAnyAuthority('PERMISSION_properties:write','PERMISSION_properties:write_all','PERMISSION_properties:write_own')")
    public ResponseEntity<Void> deleteFloorPlanPhoto(
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @PathVariable UUID photoId) {
        propertyService.deleteFloorPlanPhoto(id, planId, photoId);
        return ResponseEntity.noContent().build();
    }
}
