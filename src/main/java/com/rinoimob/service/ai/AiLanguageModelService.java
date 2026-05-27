package com.rinoimob.service.ai;

/**
 * Interface genérica para serviços de modelos de linguagem AI.
 * Segue o princípio de Inversão de Dependência (DIP) permitindo fácil
 * troca de implementações (Gemini, OpenAI, Claude, etc.).
 *
 * @author Rinoimob AI Integration
 * @version 1.0
 */
public interface AiLanguageModelService {

    /**
     * Envia um prompt simples e retorna a resposta gerada pelo modelo.
     *
     * @param prompt O texto do prompt para o modelo processar
     * @return A resposta gerada pelo modelo de linguagem
     * @throws AiServiceException Se houver erro na chamada à API ou processamento
     */
    String generateResponse(String prompt) throws AiServiceException;

    /**
     * Envia um prompt com configurações customizadas.
     *
     * @param prompt O texto do prompt
     * @param config Configurações como temperatura, max tokens, etc.
     * @return A resposta gerada pelo modelo
     * @throws AiServiceException Se houver erro na chamada
     */
    String generateResponse(String prompt, AiGenerationConfig config) throws AiServiceException;

    /**
     * Verifica se o serviço está disponível e configurado corretamente.
     *
     * @return true se o serviço está pronto, false caso contrário
     */
    boolean isAvailable();
}
