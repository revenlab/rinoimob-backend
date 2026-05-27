package com.rinoimob.service.ai;

/**
 * Exceção lançada quando ocorre um erro na integração com serviços de AI.
 * Encapsula erros de configuração, conectividade e processamento.
 */
public class AiServiceException extends Exception {

    private static final long serialVersionUID = 1L;

    private final AiErrorType errorType;

    public AiServiceException(String message) {
        super(message);
        this.errorType = AiErrorType.UNKNOWN;
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
        this.errorType = AiErrorType.UNKNOWN;
    }

    public AiServiceException(String message, AiErrorType errorType) {
        super(message);
        this.errorType = errorType;
    }

    public AiServiceException(String message, Throwable cause, AiErrorType errorType) {
        super(message, cause);
        this.errorType = errorType;
    }

    public AiErrorType getErrorType() {
        return errorType;
    }

    /**
     * Tipos de erros que podem ocorrer em chamadas AI.
     */
    public enum AiErrorType {
        CONFIGURATION_ERROR("Erro de configuração do serviço AI"),
        API_ERROR("Erro na API do provedor"),
        RATE_LIMIT_ERROR("Limite de requisições atingido"),
        INVALID_REQUEST("Request inválida"),
        CONNECTION_ERROR("Erro de conexão"),
        TIMEOUT_ERROR("Timeout na requisição"),
        UNAUTHORIZED("Não autorizado - verifique a API Key"),
        UNKNOWN("Erro desconhecido");

        private final String description;

        AiErrorType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
