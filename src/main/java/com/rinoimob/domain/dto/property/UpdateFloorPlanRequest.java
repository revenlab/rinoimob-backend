package com.rinoimob.domain.dto.property;

import java.math.BigDecimal;

public record UpdateFloorPlanRequest(
        String name,
        BigDecimal area,
        BigDecimal priceFrom,
        BigDecimal priceTo,
        Integer bedrooms,
        Integer suites,
        Integer bathrooms,
        Integer parking
) {}
