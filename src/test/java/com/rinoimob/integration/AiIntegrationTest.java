package com.rinoimob.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.domain.dto.ai.AiPromptRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Teste de integração para a API de IA (Gemini).
 * 
 * Testa:
 * - GET  /api/v1/ai/status - Verificar disponibilidade do serviço
 * - POST /api/v1/ai/generate - Gerar resposta com prompt
 * 
 * Executar com: mvn test -Dtest=AiIntegrationTest
 */
@DisplayName("AI Gemini Integration Tests")
@WithMockUser(username = "test@example.com", roles = "USER")
public class AiIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/v1/ai/status - deve retornar disponibilidade do serviço")
    void testAiStatusEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/ai/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.available", instanceOf(Boolean.class)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/ai/generate - deve gerar resposta para prompt válido")
    void testAiGenerateEndpoint_ValidPrompt() throws Exception {
        // Arrange
        AiPromptRequest request = new AiPromptRequest(
                "Gere um título curto para um apartamento com 3 quartos"
        );

        String requestBody = objectMapper.writeValueAsString(request);

        // Act & Assert
        mockMvc.perform(post("/api/v1/ai/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.prompt", notNullValue()))
                .andExpect(jsonPath("$.response", notNullValue()))
                .andExpect(jsonPath("$.response", not(emptyString())));
    }

    @Test
    @DisplayName("POST /api/v1/ai/generate - deve retornar erro para prompt vazio")
    void testAiGenerateEndpoint_EmptyPrompt() throws Exception {
        // Arrange
        AiPromptRequest request = new AiPromptRequest("");

        String requestBody = objectMapper.writeValueAsString(request);

        // Act & Assert
        mockMvc.perform(post("/api/v1/ai/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/ai/generate - deve gerar descrição criativa")
    void testAiGenerateEndpoint_CreativeResponse() throws Exception {
        // Arrange
        AiPromptRequest request = new AiPromptRequest(
                "Descreva um apartamento de forma criativa: 3 quartos, 150m², em São Paulo"
        );

        String requestBody = objectMapper.writeValueAsString(request);

        // Act & Assert
        mockMvc.perform(post("/api/v1/ai/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.response", notNullValue()))
                .andExpect(jsonPath("$.response", not(emptyString())));
    }

    @Test
    @DisplayName("POST /api/v1/ai/generate - deve gerar resposta para lista de cidades")
    void testAiGenerateEndpoint_ListResponse() throws Exception {
        // Arrange
        AiPromptRequest request = new AiPromptRequest(
                "Liste 5 cidades brasileiras principais"
        );

        String requestBody = objectMapper.writeValueAsString(request);

        // Act & Assert
        mockMvc.perform(post("/api/v1/ai/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.response", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/ai/generate - deve processar prompt longo")
    void testAiGenerateEndpoint_LongPrompt() throws Exception {
        // Arrange
        String longPrompt = "Gere um título para um imóvel com as seguintes características: " +
                "apartamento de 3 quartos, sendo uma suíte master, 2 suítes e 2 banheiros adicionais, " +
                "área total de 180m², localizado no bairro de Pinheiros em São Paulo, " +
                "com vista para a serra da cantareira, próximo a comércios e transportes públicos.";
        
        AiPromptRequest request = new AiPromptRequest(longPrompt);

        String requestBody = objectMapper.writeValueAsString(request);

        // Act & Assert
        mockMvc.perform(post("/api/v1/ai/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.response", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/ai/generate - deve processar pergunta simples")
    void testAiGenerateEndpoint_SimpleQuestion() throws Exception {
        // Arrange
        AiPromptRequest request = new AiPromptRequest(
                "Qual é a capital do Brasil?"
        );

        String requestBody = objectMapper.writeValueAsString(request);

        // Act & Assert
        mockMvc.perform(post("/api/v1/ai/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.response", notNullValue()))
                .andExpect(jsonPath("$.generatedAt", notNullValue()));
    }
}
