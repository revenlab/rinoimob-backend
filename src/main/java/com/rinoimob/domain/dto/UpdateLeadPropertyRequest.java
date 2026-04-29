package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.InterestLevel;
import jakarta.validation.constraints.NotNull;

public record UpdateLeadPropertyRequest(@NotNull InterestLevel interestLevel) {}
