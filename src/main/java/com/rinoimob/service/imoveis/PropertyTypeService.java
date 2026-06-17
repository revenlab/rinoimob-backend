package com.rinoimob.service.imoveis;

import com.rinoimob.domain.dto.PropertyTypeResponse;
import com.rinoimob.domain.dto.UpdatePropertyTypeRequest;
import com.rinoimob.domain.entity.TenantPropertyType;
import com.rinoimob.domain.enums.PropertyType;
import com.rinoimob.domain.repository.TenantPropertyTypeRepository;
import com.rinoimob.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PropertyTypeService {

    private static final Map<PropertyType, String> DEFAULT_LABELS = Map.of(
            PropertyType.HOUSE, "Casa",
            PropertyType.APARTMENT, "Apartamento",
            PropertyType.LAND, "Terreno",
            PropertyType.COMMERCIAL, "Comercial",
            PropertyType.RURAL, "Rural"
    );

    private final TenantPropertyTypeRepository tenantPropertyTypeRepository;
    private final FileStorageService fileStorageService;

    public List<PropertyTypeResponse> list(UUID tenantId) {
        provisionDefaults(tenantId);
        return tenantPropertyTypeRepository.findByTenantIdOrderByPositionAscLabelAsc(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<PropertyTypeResponse> listActive(UUID tenantId) {
        provisionDefaults(tenantId);
        return tenantPropertyTypeRepository.findByTenantIdAndActiveTrueOrderByPositionAscLabelAsc(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public PropertyTypeResponse update(UUID tenantId, PropertyType code, UpdatePropertyTypeRequest request) {
        provisionDefaults(tenantId);
        TenantPropertyType type = tenantPropertyTypeRepository.findByTenantIdAndCode(tenantId, code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property type not found"));
        if (request.label() != null) {
            String label = request.label().trim();
            if (label.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "label is required");
            }
            type.setLabel(label);
        }
        if (request.position() != null) type.setPosition(request.position());
        if (request.active() != null) type.setActive(request.active());
        if (request.cardColor() != null) {
            type.setCardColor(normalizeCardColor(request.cardColor()));
        }
        return toResponse(tenantPropertyTypeRepository.save(type));
    }

    public PropertyTypeResponse uploadCoverImage(UUID tenantId, PropertyType code, MultipartFile file) {
        validateCoverImage(file);
        TenantPropertyType type = getType(tenantId, code);
        deleteStoredFile(type.getCoverImageFid(), type.getCoverImageUrl());

        FileStorageService.UploadResult uploadResult = fileStorageService.upload(file);
        type.setCoverImageFid(uploadResult.fid());
        type.setCoverImageUrl(uploadResult.url());
        return toResponse(tenantPropertyTypeRepository.save(type));
    }

    public PropertyTypeResponse deleteCoverImage(UUID tenantId, PropertyType code) {
        TenantPropertyType type = getType(tenantId, code);
        deleteStoredFile(type.getCoverImageFid(), type.getCoverImageUrl());
        type.setCoverImageFid(null);
        type.setCoverImageUrl(null);
        return toResponse(tenantPropertyTypeRepository.save(type));
    }

    public void assertActive(UUID tenantId, PropertyType code) {
        provisionDefaults(tenantId);
        TenantPropertyType type = tenantPropertyTypeRepository.findByTenantIdAndCode(tenantId, code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid property type"));
        if (!type.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Property type is inactive");
        }
    }

    public void provisionDefaults(UUID tenantId) {
        Map<PropertyType, TenantPropertyType> existingByCode = tenantPropertyTypeRepository
                .findByTenantIdOrderByPositionAscLabelAsc(tenantId)
                .stream()
                .collect(Collectors.toMap(TenantPropertyType::getCode, type -> type));

        List<TenantPropertyType> missing = Arrays.stream(PropertyType.values())
                .filter(code -> !existingByCode.containsKey(code))
                .sorted(Comparator.comparingInt(PropertyTypeService::defaultPosition))
                .map(code -> defaultType(tenantId, code))
                .toList();

        if (!missing.isEmpty()) {
            tenantPropertyTypeRepository.saveAll(missing);
        }
    }

    private TenantPropertyType defaultType(UUID tenantId, PropertyType code) {
        TenantPropertyType type = new TenantPropertyType();
        type.setTenantId(tenantId);
        type.setCode(code);
        type.setLabel(DEFAULT_LABELS.get(code));
        type.setPosition(defaultPosition(code));
        type.setActive(true);
        return type;
    }

    private static int defaultPosition(PropertyType code) {
        return switch (code) {
            case HOUSE -> 10;
            case APARTMENT -> 20;
            case LAND -> 30;
            case COMMERCIAL -> 40;
            case RURAL -> 50;
        };
    }

    private TenantPropertyType getType(UUID tenantId, PropertyType code) {
        provisionDefaults(tenantId);
        return tenantPropertyTypeRepository.findByTenantIdAndCode(tenantId, code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property type not found"));
    }

    private String normalizeCardColor(String rawColor) {
        String value = rawColor.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (!value.matches("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cardColor must be a valid hex color");
        }
        return value.toUpperCase();
    }

    private void validateCoverImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cover image file is required");
        }
        String contentType = file.getContentType();
        boolean valid = MediaType.IMAGE_JPEG_VALUE.equals(contentType)
                || "image/jpg".equals(contentType)
                || MediaType.IMAGE_PNG_VALUE.equals(contentType)
                || "image/webp".equals(contentType);
        if (contentType == null || !valid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cover image must be JPG, PNG or WEBP");
        }
    }

    private void deleteStoredFile(String fid, String url) {
        if (fid != null && !fid.isBlank()) {
            fileStorageService.delete(fid, url);
        }
    }

    private PropertyTypeResponse toResponse(TenantPropertyType type) {
        return new PropertyTypeResponse(
                type.getId(),
                type.getCode(),
                type.getLabel(),
                type.getPosition(),
                type.isActive(),
                type.getCardColor(),
                type.getCoverImageUrl()
        );
    }
}
