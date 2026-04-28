package com.rinoimob.domain.dto.property;

import com.rinoimob.domain.enums.PropertyCondition;
import com.rinoimob.domain.enums.PropertyOperation;
import com.rinoimob.domain.enums.PropertyStatus;
import com.rinoimob.domain.enums.PropertyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreatePropertyRequest(
        @NotBlank String title,
        String description,
        @NotNull PropertyOperation operation,
        @NotNull PropertyType propertyType,
        PropertyStatus status,
        PropertyCondition condition,
        String referenceCode,
        BigDecimal price,
        String currency,
        BigDecimal taxes,
        BigDecimal condoFee,
        BigDecimal areaTotal,
        BigDecimal areaUseful,
        Integer bedrooms,
        Integer suites,
        Integer bathrooms,
        Integer parking,
        Integer floorNumber,
        String addressStreet,
        String addressNumber,
        String addressComplement,
        String addressNeighborhood,
        String addressCity,
        String addressState,
        String addressCountry,
        String addressZip,
        BigDecimal lat,
        BigDecimal lng,
        Map<String, Object> attributes,
        List<UUID> categoryIds
) {}
