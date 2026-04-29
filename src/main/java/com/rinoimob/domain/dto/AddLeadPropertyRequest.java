package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.InterestLevel;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddLeadPropertyRequest(
        @NotNull UUID propertyId,
        InterestLevel interestLevel
) {}
