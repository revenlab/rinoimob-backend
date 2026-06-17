package com.rinoimob.domain.dto;

import jakarta.validation.constraints.Size;

public record UpdatePropertyTypeRequest(
        @Size(max = 100) String label,
        Integer position,
        Boolean active
) {}
