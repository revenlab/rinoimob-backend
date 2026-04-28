package com.rinoimob.domain.dto.property;

import com.rinoimob.domain.dto.CategoryResponse;
import com.rinoimob.domain.enums.PropertyCondition;
import com.rinoimob.domain.enums.PropertyOperation;
import com.rinoimob.domain.enums.PropertyStatus;
import com.rinoimob.domain.enums.PropertyType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PropertySummaryResponse(
        UUID id,
        String title,
        PropertyOperation operation,
        PropertyType propertyType,
        PropertyStatus status,
        PropertyCondition condition,
        String referenceCode,
        BigDecimal price,
        String currency,
        BigDecimal areaTotal,
        Integer bedrooms,
        Integer bathrooms,
        String addressCity,
        String addressState,
        String addressCountry,
        UUID coverPhotoId,
        String coverPhotoUrl,
        List<CategoryResponse> categories,
        LocalDateTime createdAt
) {}
