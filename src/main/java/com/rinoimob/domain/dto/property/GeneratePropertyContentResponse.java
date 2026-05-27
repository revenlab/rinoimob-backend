package com.rinoimob.domain.dto.property;

/**
 * DTO para resposta de geração de título e descrição via IA.
 */
public record GeneratePropertyContentResponse(
        String title,
        String description,
        Long generatedAtMs
) {
}
