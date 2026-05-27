package com.rinoimob.api.controller.ai;

import com.rinoimob.domain.dto.ai.AiPromptRequest;
import com.rinoimob.domain.dto.ai.AiPromptResponse;
import com.rinoimob.service.ai.AiLanguageModelService;
import com.rinoimob.service.ai.AiServiceException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * Controller para integração com serviços de AI.
 * Exemplo de como utilizar o AiLanguageModelService genérico.
 * 
 * Pode processar prompts para geração de títulos, descrições, etc.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "Endpoints para integração com serviços de IA")
public class AiController {

    private final AiLanguageModelService aiService;

    /**
     * Processa um prompt genérico e retorna uma resposta gerada por IA.
     *
     * @param request Contém o prompt a ser processado
     * @return Response com o texto gerado
     */
    @PostMapping("/generate")
    @Operation(summary = "Gera resposta baseada em prompt", description = "Processa um prompt com o modelo de IA configurado")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resposta gerada com sucesso",
                    content = @Content(schema = @Schema(implementation = AiPromptResponse.class))),
            @ApiResponse(responseCode = "400", description = "Prompt inválido"),
            @ApiResponse(responseCode = "503", description = "Serviço de IA indisponível"),
            @ApiResponse(responseCode = "429", description = "Limite de requisições atingido")
    })
    public ResponseEntity<AiPromptResponse> generate(@RequestBody AiPromptRequest request) {
        log.info("Recebida requisição de geração de IA com {} caracteres", request.prompt().length());

        if (!aiService.isAvailable()) {
            log.error("Serviço de IA não disponível");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Serviço de IA não está disponível no momento");
        }

        try {
            String generatedText = aiService.generateResponse(request.prompt());
            
            AiPromptResponse response = new AiPromptResponse(
                    request.prompt(),
                    generatedText,
                    LocalDateTime.now()
            );
            
            log.info("Resposta gerada com sucesso: {} caracteres", generatedText.length());
            return ResponseEntity.ok(response);

        } catch (AiServiceException e) {
            log.error("Erro ao processar prompt com IA: {}", e.getMessage(), e);

            // Mapear tipos de erro para códigos HTTP apropriados
            if (e.getErrorType() == AiServiceException.AiErrorType.RATE_LIMIT_ERROR) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Limite de requisições atingido: " + e.getMessage());
            } else if (e.getErrorType() == AiServiceException.AiErrorType.UNAUTHORIZED) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Serviço de IA não autenticado");
            } else if (e.getErrorType() == AiServiceException.AiErrorType.INVALID_REQUEST) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            } else {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Erro ao processar requisição de IA: " + e.getMessage());
            }
        }
    }

    /**
     * Verifica a disponibilidade do serviço de IA.
     */
    @GetMapping("/status")
    @Operation(summary = "Verifica status do serviço de IA", description = "Retorna se o serviço está disponível")
    @ApiResponse(responseCode = "200", description = "Status do serviço retornado")
    public ResponseEntity<Object> status() {
        return ResponseEntity.ok(new Object() {
            public final boolean available = aiService.isAvailable();
            public final String message = available ? "Serviço disponível" : "Serviço indisponível";
        });
    }
}
