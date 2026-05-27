package com.rinoimob.domain.dto.ai;

/**
 * DTO para requisição de geração de conteúdo via IA.
 *
 * @param prompt O texto do prompt a ser processado
 */
public record AiPromptRequest(String prompt) {
}
