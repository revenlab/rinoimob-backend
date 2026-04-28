package com.rinoimob.domain.dto.property;

import com.rinoimob.domain.dto.CategoryResponse;
import com.rinoimob.domain.enums.PropertyCondition;
import com.rinoimob.domain.enums.PropertyOperation;
import com.rinoimob.domain.enums.PropertyStatus;
import com.rinoimob.domain.enums.PropertyType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PropertyResponse(
        UUID id,
        String title,
        String description,
        PropertyOperation operation,
        PropertyType propertyType,
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
        UUID coverPhotoId,
        Map<String, Object> attributes,
        List<CategoryResponse> categories,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<PropertyPhotoResponse> photos,
        List<FloorPlanResponse> floorPlans
) {}
