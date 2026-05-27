package com.rinoimob.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Implementação mock/fallback do serviço de IA.
 * Usada quando nenhum provedor real está configurado ou em desenvolvimento.
 * 
 * Útil para:
 * - Testes locais sem dependência de API externa
 * - Desenvolvimento sem custos de API
 * - Fallback quando principal está indisponível
 *
 * @author Rinoimob AI Integration
 */
@Slf4j
@Service
@ConditionalOnProperty(
    name = "ai.provider",
    havingValue = "mock",
    matchIfMissing = false
)
public class MockAiService implements AiLanguageModelService {

    @Override
    public String generateResponse(String prompt) throws AiServiceException {
        return generateResponse(prompt, new AiGenerationConfig());
    }

    @Override
    public String generateResponse(String prompt, AiGenerationConfig config) throws AiServiceException {
        if (prompt == null || prompt.isBlank()) {
            throw new AiServiceException(
                    "Prompt não pode ser vazio.",
                    AiServiceException.AiErrorType.INVALID_REQUEST
            );
        }

        log.debug("MockAiService: processando prompt (modo mock)");
        
        // Retorna uma resposta simulada baseada no tipo de prompt
        return generateMockResponse(prompt);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    /**
     * Gera resposta mockada baseada no conteúdo do prompt.
     */
    private String generateMockResponse(String prompt) {
        String lower = prompt.toLowerCase();

        if (lower.contains("titulo") || lower.contains("title")) {
            return "Casa moderna com 3 quartos em São Paulo";
        }

        if (lower.contains("descrição") || lower.contains("description")) {
            return "Imóvel bem localizado com excelente infraestrutura, próximo a escolas e shopping. " +
                   "Possui 3 quartos, 2 banheiros, sala ampla e cozinha planejada.";
        }

        if (lower.contains("vantagem") || lower.contains("benefit")) {
            return "• Localização excelente\n• Acabamento de qualidade\n• Área bem distribuída";
        }

        if (lower.contains("preço") || lower.contains("price")) {
            return "Avaliação de mercado: R$ 450.000 a R$ 500.000";
        }

        // Resposta genérica
        return "Esta é uma resposta simulada do MockAiService. Configure um provedor real em application.yml";
    }
}
