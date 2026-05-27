package com.rinoimob.domain.dto.ai;

import java.time.LocalDateTime;

/**
 * DTO para resposta de geração de conteúdo via IA.
 *
 * @param prompt O prompt original enviado
 * @param response O conteúdo gerado pela IA
 * @param generatedAt Timestamp de quando foi gerado
 */
public record AiPromptResponse(
        String prompt,
        String response,
        LocalDateTime generatedAt
) {
}
