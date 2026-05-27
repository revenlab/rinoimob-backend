package com.rinoimob.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviço para gerar conteúdo de propriedades usando IA.
 * Utiliza AiLanguageModelService para manter desacoplamento de provedor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyAiService {

    private final AiLanguageModelService aiService;

    /**
     * Gera um título atraente para uma propriedade com base em seus atributos.
     */
    public String generatePropertyTitle(
            String bedrooms,
            String bathrooms,
            String suites,
            String propertyType,
            String city,
            String neighborhood,
            BigDecimal price,
            String currency,
            String operation) {

        if (!aiService.isAvailable()) {
            log.warn("Serviço de IA não disponível para gerar título");
            return null;
        }

        String prompt = buildTitlePrompt(
                bedrooms, bathrooms, suites, propertyType,
                city, neighborhood, price, currency, operation
        );

        try {
            String title = aiService.generateResponse(prompt);
            // Limitar a 100 caracteres para caber em campo de título
            if (title.length() > 100) {
                title = title.substring(0, 97) + "...";
            }
            log.info("Título gerado com sucesso via IA: {} caracteres", title.length());
            return title;
        } catch (Exception e) {
            log.error("Erro ao gerar título com IA: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gera uma descrição atraente para uma propriedade com base em seus atributos.
     */
    public String generatePropertyDescription(
            String bedrooms,
            String bathrooms,
            String suites,
            String parking,
            String areaTotal,
            String propertyType,
            String city,
            String neighborhood,
            BigDecimal price,
            String currency,
            String operation,
            String photoDescription) {

        if (!aiService.isAvailable()) {
            log.warn("Serviço de IA não disponível para gerar descrição");
            return null;
        }

        String prompt = buildDescriptionPrompt(
                bedrooms, bathrooms, suites, parking, areaTotal,
                propertyType, city, neighborhood, price, currency, operation, photoDescription
        );

        try {
            String description = aiService.generateResponse(prompt);
            log.info("Descrição gerada com sucesso via IA: {} caracteres", description.length());
            return description;
        } catch (Exception e) {
            log.error("Erro ao gerar descrição com IA: {}", e.getMessage());
            return null;
        }
    }

    private String buildTitlePrompt(
            String bedrooms, String bathrooms, String suites,
            String propertyType, String city, String neighborhood,
            BigDecimal price, String currency, String operation) {

        List<String> features = new ArrayList<>();

        if (bedrooms != null) features.add(bedrooms + " quarto(s)");
        if (bathrooms != null) features.add(bathrooms + " banheiro(s)");
        if (suites != null) features.add(suites + " suíte(s)");
        if (propertyType != null) features.add(propertyType);
        if (city != null) features.add("em " + city);
        if (neighborhood != null) features.add("no bairro " + neighborhood);
        if (operation != null) features.add(operation.toLowerCase());

        String operationText = operation != null ? operation.toLowerCase() : "venda";

        return String.format(
                "Gere um título curto (máximo 80 caracteres) e atraente para um anúncio de " +
                "%s com os seguintes detalhes: %s%s. " +
                "O título deve ser profissional, impactante e ideal para atrair compradores/locatários. " +
                "Responda apenas com o título, sem explicações adicionais.",
                operationText,
                String.join(", ", features),
                price != null ? String.format(", preço: %s %s", price, currency != null ? currency : "") : ""
        );
    }

    private String buildDescriptionPrompt(
            String bedrooms, String bathrooms, String suites, String parking, String areaTotal,
            String propertyType, String city, String neighborhood,
            BigDecimal price, String currency, String operation, String photoDescription) {

        List<String> features = new ArrayList<>();

        if (bedrooms != null) features.add(bedrooms + " quarto(s)");
        if (bathrooms != null) features.add(bathrooms + " banheiro(s)");
        if (suites != null) features.add(suites + " suíte(s)");
        if (parking != null) features.add(parking + " vaga(s) de garagem");
        if (areaTotal != null) features.add("área total de " + areaTotal + " m²");

        String operationText = operation != null ? operation.toLowerCase() : "venda";
        String photoInfo = photoDescription != null && !photoDescription.isEmpty() ?
                String.format("\nDados da foto principal: %s", photoDescription) : "";

        return String.format(
                "Gere uma descrição atraente (2-3 linhas) para um anúncio de %s com os seguintes detalhes:\n" +
                "- Tipo: %s\n" +
                "- Localização: %s%s\n" +
                "- Características: %s%s\n" +
                "- Preço: %s %s%s\n\n" +
                "A descrição deve ser persuasiva, profissional e destacar os pontos positivos do imóvel. " +
                "Responda apenas com a descrição, sem títulos ou explicações adicionais.",
                operationText,
                propertyType != null ? propertyType : "imóvel",
                city != null ? city : "desconhecida",
                neighborhood != null ? " - " + neighborhood : "",
                String.join(", ", features),
                photoInfo,
                price != null ? price : "a combinar",
                currency != null ? currency : "",
                operation != null && operation.equalsIgnoreCase("RENT") ? " por mês" : ""
        );
    }
}
