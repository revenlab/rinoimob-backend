package com.rinoimob.service.ai;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Implementação do serviço de IA utilizando Google Gemini SDK oficial.
 * 
 * Segue o princípio de Responsabilidade Única (SRP):
 * - Únicamente responsável por integração com Gemini
 * - Abstração das complexidades da SDK Gemini
 * - Tratamento de erros específicos de Gemini
 *
 * SDK Reference: https://github.com/googleapis/java-genai
 * Models: https://ai.google.dev/gemini-api/docs/models
 *
 * @author Rinoimob AI Integration
 * @version 2.0
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiAiService implements AiLanguageModelService {

    private final String geminiModel;
    private Client geminiClient;
    private boolean initialized = false;

    /**
     * Construtor com injeção de dependência.
     * A API Key deve ser configurada em GOOGLE_API_KEY ou AI_GEMINI_API_KEY
     *
     * @param geminiApiKey Chave de API do Gemini (de AI_GEMINI_API_KEY)
     * @param geminiModel Modelo a usar (padrão: gemini-2.5-flash)
     */
    public GeminiAiService(
            @Value("${ai.gemini.api-key:}") String geminiApiKey,
            @Value("${ai.gemini.model:gemini-2.5-flash}") String geminiModel) {
        this.geminiModel = geminiModel;
        
        // Criar cliente Gemini com a chave de API
        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            try {
                this.geminiClient = Client.builder()
                    .apiKey(geminiApiKey)
                    .build();
                this.initialized = true;
                log.info("Gemini AI Service inicializado com sucesso. Modelo: {}", geminiModel);
            } catch (Exception e) {
                log.error("Erro ao inicializar Gemini AI Service: {}", e.getMessage(), e);
                this.geminiClient = null;
                this.initialized = false;
            }
        } else {
            log.warn("Gemini API Key não configurada. Serviço será desabilitado.");
            this.geminiClient = null;
            this.initialized = false;
        }
    }

    /**
     * Envia um prompt simples para o Gemini e retorna a resposta.
     *
     * @param prompt Texto do prompt
     * @return Resposta gerada pelo Gemini
     * @throws AiServiceException Se houver erro na chamada
     */
    @Override
    public String generateResponse(String prompt) throws AiServiceException {
        return generateResponse(prompt, new AiGenerationConfig());
    }

    /**
     * Envia um prompt com configurações customizadas para o Gemini.
     *
     * @param prompt Texto do prompt
     * @param config Configurações (temperatura, maxTokens, etc.)
     * @return Resposta gerada
     * @throws AiServiceException Se houver erro
     */
    @Override
    public String generateResponse(String prompt, AiGenerationConfig generationConfig) throws AiServiceException {
        if (!isAvailable()) {
            throw new AiServiceException(
                    "Serviço Gemini não está disponível. Verifique a configuração da API Key.",
                    AiServiceException.AiErrorType.CONFIGURATION_ERROR
            );
        }

        if (prompt == null || prompt.isBlank()) {
            throw new AiServiceException(
                    "Prompt não pode ser vazio.",
                    AiServiceException.AiErrorType.INVALID_REQUEST
            );
        }

        try {
            log.debug("Enviando prompt para Gemini (modelo: {}): {} caracteres", 
                    geminiModel, prompt.length());

            // Usar a SDK oficial para gerar conteúdo
            GenerateContentResponse response = geminiClient.models.generateContent(
                    geminiModel,
                    prompt,
                    null  // usar configurações padrão
            );

            // Extrair o texto da resposta usando o método text() da SDK
            String responseText = response.text();

            if (responseText == null || responseText.isBlank()) {
                log.warn("Resposta vazia do Gemini para prompt: {}", 
                        prompt.substring(0, Math.min(50, prompt.length())));
                return "";
            }

            log.debug("Resposta recebida do Gemini: {} caracteres", responseText.length());
            return responseText;

        } catch (Exception e) {
            return handleGeminiError(e);
        }
    }

    /**
     * Trata exceções da SDK Gemini e mapeia para tipos de erro apropriados.
     */
    private String handleGeminiError(Exception e) throws AiServiceException {
        String message = e.getMessage() != null ? e.getMessage() : "Erro desconhecido";
        
        log.error("Erro na SDK Gemini: {}", message, e);

        AiServiceException.AiErrorType errorType = AiServiceException.AiErrorType.API_ERROR;

        // Mapear exceções comuns
        if (message.contains("401") || message.contains("UNAUTHENTICATED")) {
            errorType = AiServiceException.AiErrorType.UNAUTHORIZED;
            log.error("Falha de autenticação Gemini: verifique a API Key");
        } else if (message.contains("429") || message.contains("RESOURCE_EXHAUSTED")) {
            errorType = AiServiceException.AiErrorType.RATE_LIMIT_ERROR;
            log.warn("Rate limit atingido no Gemini");
        } else if (message.contains("400") || message.contains("INVALID_ARGUMENT")) {
            errorType = AiServiceException.AiErrorType.INVALID_REQUEST;
            log.error("Request inválida para Gemini");
        } else if (message.contains("408") || message.contains("504") || message.contains("DEADLINE_EXCEEDED")) {
            errorType = AiServiceException.AiErrorType.TIMEOUT_ERROR;
            log.error("Timeout na chamada Gemini");
        }

        throw new AiServiceException(
                "Erro na API Gemini: " + message,
                e,
                errorType
        );
    }

    /**
     * Verifica se o serviço Gemini está disponível e configurado.
     *
     * @return true se inicializado com sucesso, false caso contrário
     */
    @Override
    public boolean isAvailable() {
        return initialized && geminiClient != null;
    }

    /**
     * Retorna o modelo Gemini em uso.
     */
    public String getModelName() {
        return geminiModel;
    }
}

