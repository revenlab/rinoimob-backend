package com.rinoimob.service.imoveis;

import com.rinoimob.domain.dto.PropertyTypeResponse;
import com.rinoimob.domain.dto.UpdatePropertyTypeRequest;
import com.rinoimob.domain.entity.TenantPropertyType;
import com.rinoimob.domain.enums.PropertyType;
import com.rinoimob.domain.repository.TenantPropertyTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    public List<PropertyTypeResponse> list(UUID tenantId) {
        ensureDefaults(tenantId);
        return tenantPropertyTypeRepository.findByTenantIdOrderByPositionAscLabelAsc(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<PropertyTypeResponse> listActive(UUID tenantId) {
        ensureDefaults(tenantId);
        return tenantPropertyTypeRepository.findByTenantIdAndActiveTrueOrderByPositionAscLabelAsc(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public PropertyTypeResponse update(UUID tenantId, PropertyType code, UpdatePropertyTypeRequest request) {
        ensureDefaults(tenantId);
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
        return toResponse(tenantPropertyTypeRepository.save(type));
    }

    public void assertActive(UUID tenantId, PropertyType code) {
        ensureDefaults(tenantId);
        TenantPropertyType type = tenantPropertyTypeRepository.findByTenantIdAndCode(tenantId, code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid property type"));
        if (!type.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Property type is inactive");
        }
    }

    private void ensureDefaults(UUID tenantId) {
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

    private PropertyTypeResponse toResponse(TenantPropertyType type) {
        return new PropertyTypeResponse(
                type.getId(),
                type.getCode(),
                type.getLabel(),
                type.getPosition(),
                type.isActive()
        );
    }
}
