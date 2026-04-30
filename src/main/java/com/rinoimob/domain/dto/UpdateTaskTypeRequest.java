package com.rinoimob.domain.dto;

public record UpdateTaskTypeRequest(
        String name,
        String color,
        String icon,
        Integer position,
        Boolean active
) {}
