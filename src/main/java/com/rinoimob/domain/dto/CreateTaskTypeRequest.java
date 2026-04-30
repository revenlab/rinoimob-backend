package com.rinoimob.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTaskTypeRequest(
        @NotBlank String name,
        String color,
        String icon,
        Integer position
) {}
