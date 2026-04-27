package com.rinoimob.domain.dto.property;

import com.rinoimob.domain.enums.PropertyOperation;
import com.rinoimob.domain.enums.PropertyStatus;
import com.rinoimob.domain.enums.PropertyType;

import java.math.BigDecimal;
import java.util.Map;

public record UpdatePropertyRequest(
        String title,
        String description,
        PropertyOperation operation,
        PropertyType propertyType,
        PropertyStatus status,
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
        Map<String, Object> attributes
) {}
