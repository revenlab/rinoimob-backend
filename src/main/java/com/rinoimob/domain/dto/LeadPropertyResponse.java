package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.InterestLevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record LeadPropertyResponse(
        UUID id,
        UUID leadId,
        UUID propertyId,
        InterestLevel interestLevel,
        LocalDateTime createdAt,
        String propertyTitle,
        String propertyOperation,
        BigDecimal propertyPrice,
        String propertyCurrency,
        String addressCity,
        String addressState,
        String coverPhotoUrl
) {}
