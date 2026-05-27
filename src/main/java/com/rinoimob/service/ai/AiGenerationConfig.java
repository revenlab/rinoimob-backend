package com.rinoimob.service.ai;

/**
 * Configurações para geração de respostas com o modelo de linguagem.
 * Permite customização de comportamento sem modificar a interface principal.
 */
public class AiGenerationConfig {

    /**
     * Temperatura: controla o nível de criatividade/randomização (0.0 a 2.0).
     * Valores baixos (0.0-0.5) = mais determinístico
     * Valores altos (0.7-1.5) = mais criativo
     */
    private Double temperature = 0.7;

    /**
     * Número máximo de tokens na resposta.
     */
    private Integer maxTokens = 1024;

    /**
     * Núcleo de probabilidade para amostragem (0.0 a 1.0).
     * Apenas tokens que somam topP de probabilidade são considerados.
     */
    private Double topP;

    /**
     * Número de top K tokens a considerar.
     */
    private Integer topK;

    /**
     * Palavras ou frases para penalizar na resposta.
     */
    private String[] stopSequences;

    /**
     * Se deve usar cache de requisição (para economizar tokens).
     */
    private Boolean useCache = false;

    public AiGenerationConfig() {
    }

    public AiGenerationConfig(Double temperature, Integer maxTokens) {
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    // Getters e Setters
    public Double getTemperature() {
        return temperature;
    }

    public AiGenerationConfig setTemperature(Double temperature) {
        this.temperature = temperature;
        return this;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public AiGenerationConfig setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }

    public Double getTopP() {
        return topP;
    }

    public AiGenerationConfig setTopP(Double topP) {
        this.topP = topP;
        return this;
    }

    public Integer getTopK() {
        return topK;
    }

    public AiGenerationConfig setTopK(Integer topK) {
        this.topK = topK;
        return this;
    }

    public String[] getStopSequences() {
        return stopSequences;
    }

    public AiGenerationConfig setStopSequences(String[] stopSequences) {
        this.stopSequences = stopSequences;
        return this;
    }

    public Boolean getUseCache() {
        return useCache;
    }

    public AiGenerationConfig setUseCache(Boolean useCache) {
        this.useCache = useCache;
        return this;
    }

    @Override
    public String toString() {
        return "AiGenerationConfig{" +
                "temperature=" + temperature +
                ", maxTokens=" + maxTokens +
                ", topP=" + topP +
                ", topK=" + topK +
                ", useCache=" + useCache +
                '}';
    }
}
