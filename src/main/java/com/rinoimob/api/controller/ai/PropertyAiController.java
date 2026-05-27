package com.rinoimob.api.controller.ai;

import com.rinoimob.domain.dto.property.GeneratePropertyContentRequest;
import com.rinoimob.domain.dto.property.GeneratePropertyContentResponse;
import com.rinoimob.service.ai.PropertyAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller para geração de conteúdo de propriedades via IA.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai/properties")
@RequiredArgsConstructor
@Tag(name = "AI - Properties", description = "Endpoints para geração de conteúdo de propriedades via IA")
public class PropertyAiController {

    private final PropertyAiService propertyAiService;

    /**
     * Gera um título atraente para uma propriedade baseado em seus atributos.
     */
    @PostMapping("/generate-title")
    @Operation(summary = "Gera título para propriedade", description = "Utiliza IA para gerar um título atraente baseado nos atributos do imóvel")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Título gerado com sucesso",
                    content = @Content(schema = @Schema(implementation = GeneratePropertyContentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "503", description = "Serviço de IA indisponível")
    })
    public ResponseEntity<GeneratePropertyContentResponse> generateTitle(
            @RequestBody GeneratePropertyContentRequest request) {

        log.info("Requisição recebida para gerar título de propriedade");

        String title = propertyAiService.generatePropertyTitle(
                request.bedrooms(),
                request.bathrooms(),
                request.suites(),
                request.propertyType(),
                request.city(),
                request.neighborhood(),
                request.price(),
                request.currency(),
                request.operation()
        );

        if (title == null) {
            return ResponseEntity.status(503).body(
                    new GeneratePropertyContentResponse(null, null, System.currentTimeMillis())
            );
        }

        return ResponseEntity.ok(
                new GeneratePropertyContentResponse(title, null, System.currentTimeMillis())
        );
    }

    /**
     * Gera uma descrição atraente para uma propriedade baseada em seus atributos.
     */
    @PostMapping("/generate-description")
    @Operation(summary = "Gera descrição para propriedade", description = "Utiliza IA para gerar uma descrição atraente baseada nos atributos do imóvel")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Descrição gerada com sucesso",
                    content = @Content(schema = @Schema(implementation = GeneratePropertyContentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "503", description = "Serviço de IA indisponível")
    })
    public ResponseEntity<GeneratePropertyContentResponse> generateDescription(
            @RequestBody GeneratePropertyContentRequest request) {

        log.info("Requisição recebida para gerar descrição de propriedade");

        String description = propertyAiService.generatePropertyDescription(
                request.bedrooms(),
                request.bathrooms(),
                request.suites(),
                request.parking(),
                request.areaTotal(),
                request.propertyType(),
                request.city(),
                request.neighborhood(),
                request.price(),
                request.currency(),
                request.operation(),
                request.photoDescription()
        );

        if (description == null) {
            return ResponseEntity.status(503).body(
                    new GeneratePropertyContentResponse(null, null, System.currentTimeMillis())
            );
        }

        return ResponseEntity.ok(
                new GeneratePropertyContentResponse(null, description, System.currentTimeMillis())
        );
    }

    /**
     * Gera tanto título quanto descrição para uma propriedade.
     */
    @PostMapping("/generate-all")
    @Operation(summary = "Gera título e descrição", description = "Utiliza IA para gerar título e descrição atraentes")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conteúdo gerado com sucesso",
                    content = @Content(schema = @Schema(implementation = GeneratePropertyContentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "503", description = "Serviço de IA indisponível")
    })
    public ResponseEntity<GeneratePropertyContentResponse> generateAll(
            @RequestBody GeneratePropertyContentRequest request) {

        log.info("Requisição recebida para gerar título e descrição de propriedade");

        String title = propertyAiService.generatePropertyTitle(
                request.bedrooms(),
                request.bathrooms(),
                request.suites(),
                request.propertyType(),
                request.city(),
                request.neighborhood(),
                request.price(),
                request.currency(),
                request.operation()
        );

        String description = propertyAiService.generatePropertyDescription(
                request.bedrooms(),
                request.bathrooms(),
                request.suites(),
                request.parking(),
                request.areaTotal(),
                request.propertyType(),
                request.city(),
                request.neighborhood(),
                request.price(),
                request.currency(),
                request.operation(),
                request.photoDescription()
        );

        if (title == null && description == null) {
            return ResponseEntity.status(503).body(
                    new GeneratePropertyContentResponse(null, null, System.currentTimeMillis())
            );
        }

        return ResponseEntity.ok(
                new GeneratePropertyContentResponse(title, description, System.currentTimeMillis())
        );
    }
}
