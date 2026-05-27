package com.rinoimob.service;

import com.rinoimob.service.ai.AiLanguageModelService;
import com.rinoimob.service.ai.AiGenerationConfig;
import com.rinoimob.service.ai.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Optional;

/**
 * Exemplo de como integrar AiLanguageModelService em serviços de negócio.
 * 
 * Este exemplo mostra como usar Gemini AI para:
 * - Gerar títulos dinâmicos para imóveis
 * - Gerar descrições automáticas
 * - Sugerir melhorias no conteúdo
 * 
 * IMPORTANTE: Este é um exemplo educacional. Adapte conforme necessário.
 * 
 * @author Rinoimob AI Integration
 */
@Slf4j
@Service
public class PropertyAiEnhancementService {

    private final AiLanguageModelService aiService;

    public PropertyAiEnhancementService(Optional<AiLanguageModelService> aiService) {
        // Optional permite que o serviço funcione mesmo sem IA disponível
        this.aiService = aiService.orElse(null);
    }

    /**
     * Gera um título atrativo para um imóvel usando Gemini.
     *
     * Exemplo de uso em PropertyService:
     * {@code
     * public PropertyResponse createProperty(CreatePropertyRequest req) {
     *     // ... lógica normal ...
     *     
     *     // Gerar título se vazio
     *     if (req.title() == null || req.title().isBlank()) {
     *         String suggestedTitle = propertyAiService.generateTitle(property);
     *         property.setTitle(suggestedTitle);
     *     }
     *     
     *     return toResponse(propertyRepository.save(property));
     * }
     * }
     */
    public String generateTitle(Property property) {
        if (!isAiAvailable()) {
            log.debug("Serviço AI não disponível, usando título padrão");
            return generateDefaultTitle(property);
        }

        try {
            String prompt = buildTitlePrompt(property);
            
            // Usar configuração customizada para títulos (menor criatividade)
            AiGenerationConfig config = new AiGenerationConfig()
                .setTemperature(0.3)  // Mais determinístico
                .setMaxTokens(64);    // Títulos curtos

            String generatedTitle = aiService.generateResponse(prompt, config);
            
            // Limitar a 60 caracteres
            if (generatedTitle != null && generatedTitle.length() > 60) {
                generatedTitle = generatedTitle.substring(0, 60).trim();
            }

            log.info("Título gerado por IA para property={}", property.getId());
            return generatedTitle;

        } catch (AiServiceException e) {
            log.warn("Erro ao gerar título com IA: {}. Usando título padrão.", e.getMessage());
            return generateDefaultTitle(property);
        }
    }

    /**
     * Gera uma descrição completa para um imóvel usando Gemini.
     *
     * Exemplo de uso em PropertyService:
     * {@code
     * public PropertyResponse createProperty(CreatePropertyRequest req) {
     *     // ... lógica normal ...
     *     
     *     if (req.description() == null || req.description().isBlank()) {
     *         String description = propertyAiService.generateDescription(property);
     *         property.setDescription(description);
     *     }
     *     
     *     return toResponse(propertyRepository.save(property));
     * }
     * }
     */
    public String generateDescription(Property property) {
        if (!isAiAvailable()) {
            return generateDefaultDescription(property);
        }

        try {
            String prompt = buildDescriptionPrompt(property);
            
            AiGenerationConfig config = new AiGenerationConfig()
                .setTemperature(0.7)     // Mais criativo
                .setMaxTokens(512);      // Descrição detalhada

            String generatedDesc = aiService.generateResponse(prompt, config);
            
            log.info("Descrição gerada por IA para property={}", property.getId());
            return generatedDesc;

        } catch (AiServiceException e) {
            log.warn("Erro ao gerar descrição: {}. Usando descrição padrão.", e.getMessage());
            return generateDefaultDescription(property);
        }
    }

    /**
     * Sugere melhorias para um anúncio existente.
     *
     * Exemplo de uso:
     * {@code
     * public List<String> getSuggestions(UUID propertyId) throws AiServiceException {
     *     Property property = propertyRepository.findById(propertyId).orElseThrow();
     *     String suggestions = propertyAiService.generateSuggestions(property);
     *     return Arrays.asList(suggestions.split("\n"));
     * }
     * }
     */
    public String generateSuggestions(Property property) throws AiServiceException {
        if (!isAiAvailable()) {
            throw new AiServiceException(
                "Serviço de sugestões não disponível no momento",
                com.rinoimob.service.ai.AiServiceException.AiErrorType.CONFIGURATION_ERROR
            );
        }

        String prompt = String.format(
            "Analise este anúncio de imóvel e forneça 3 sugestões de melhoria:\n\n" +
            "Título: %s\n" +
            "Descrição: %s\n\n" +
            "Retorne como lista numerada com sugestões práticas e específicas.",
            property.getTitle(),
            property.getDescription()
        );

        AiGenerationConfig config = new AiGenerationConfig()
            .setTemperature(0.6)
            .setMaxTokens(300);

        return aiService.generateResponse(prompt, config);
    }

    // ── Métodos auxiliares ──────────────────────────────────────────────────────

    private boolean isAiAvailable() {
        return aiService != null && aiService.isAvailable();
    }

    private String buildTitlePrompt(Property property) {
        return String.format(
            "Gere um título conciso e atraente (máximo 60 caracteres) para um imóvel:\n" +
            "- Tipo: %s\n" +
            "- Quartos: %d\n" +
            "- Banheiros: %d\n" +
            "- Área: %s m²\n" +
            "- Localização: %s, %s\n" +
            "- Operação: %s\n\n" +
            "Retorne APENAS o título, sem aspas ou numeração.",
            
            property.getPropertyType() != null ? property.getPropertyType().name() : "N/A",
            property.getBedrooms() != null ? property.getBedrooms() : 0,
            property.getBathrooms() != null ? property.getBathrooms() : 0,
            property.getAreaTotal() != null ? property.getAreaTotal() : "N/A",
            property.getAddressCity() != null ? property.getAddressCity() : "N/A",
            property.getAddressState() != null ? property.getAddressState() : "N/A",
            property.getOperation() != null ? property.getOperation().name() : "SALE"
        );
    }

    private String buildDescriptionPrompt(Property property) {
        return String.format(
            "Escreva uma descrição profissional e atrativa (200-300 palavras) para um imóvel com:\n" +
            "- Tipo: %s\n" +
            "- Quartos: %d, Banheiros: %d, Suítes: %d\n" +
            "- Área Total: %s m², Área Útil: %s m²\n" +
            "- Endereço: %s, %s, %s - %s\n" +
            "- Condição: %s\n" +
            "- Operação: %s\n\n" +
            "A descrição deve ser profissional, informativa e persuasiva para potenciais compradores/locatários.",
            
            property.getPropertyType() != null ? property.getPropertyType().name() : "Imóvel",
            property.getBedrooms() != null ? property.getBedrooms() : 0,
            property.getBathrooms() != null ? property.getBathrooms() : 0,
            property.getSuites() != null ? property.getSuites() : 0,
            property.getAreaTotal() != null ? property.getAreaTotal() : "N/A",
            property.getAreaUseful() != null ? property.getAreaUseful() : "N/A",
            property.getAddressStreet() != null ? property.getAddressStreet() : "N/A",
            property.getAddressCity() != null ? property.getAddressCity() : "N/A",
            property.getAddressState() != null ? property.getAddressState() : "N/A",
            property.getAddressZip() != null ? property.getAddressZip() : "N/A",
            property.getCondition() != null ? property.getCondition().name() : "N/A",
            property.getOperation() != null ? property.getOperation().name() : "SALE"
        );
    }

    private String generateDefaultTitle(Property property) {
        StringBuilder sb = new StringBuilder();
        
        if (property.getBedrooms() != null && property.getBedrooms() > 0) {
            sb.append(property.getBedrooms()).append("q");
        }
        
        if (sb.length() > 0) sb.append(" - ");
        
        if (property.getPropertyType() != null) {
            sb.append(property.getPropertyType().name());
        }
        
        if (property.getAddressCity() != null) {
            sb.append(" em ").append(property.getAddressCity());
        }
        
        return sb.toString();
    }

    private String generateDefaultDescription(Property property) {
        StringBuilder sb = new StringBuilder("Imóvel ");
        
        if (property.getAreaTotal() != null) {
            sb.append("com ").append(property.getAreaTotal()).append(" m²");
        }
        
        if (property.getBedrooms() != null && property.getBedrooms() > 0) {
            sb.append(", ").append(property.getBedrooms()).append(" quarto(s)");
        }
        
        if (property.getBathrooms() != null && property.getBathrooms() > 0) {
            sb.append(", ").append(property.getBathrooms()).append(" banheiro(s)");
        }
        
        if (property.getAddressCity() != null) {
            sb.append(" em ").append(property.getAddressCity());
        }
        
        sb.append(".");
        return sb.toString();
    }
}
