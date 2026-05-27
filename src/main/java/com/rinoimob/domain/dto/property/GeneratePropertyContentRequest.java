package com.rinoimob.domain.dto.property;

/**
 * DTO para requisição de geração de título e descrição via IA.
 */
public record GeneratePropertyContentRequest(
        String bedrooms,
        String bathrooms,
        String suites,
        String parking,
        String areaTotal,
        String propertyType,
        String city,
        String neighborhood,
        java.math.BigDecimal price,
        String currency,
        String operation,
        String photoDescription
) {
}
