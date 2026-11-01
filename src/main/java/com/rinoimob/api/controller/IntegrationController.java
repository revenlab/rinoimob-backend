package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.integration.*;
import com.rinoimob.service.integration.GrupoOlxPropertyFeedService;
import com.rinoimob.service.integration.IntegrationManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
public class IntegrationController {
    private final IntegrationManagementService service;
    private final GrupoOlxPropertyFeedService grupoOlxFeedService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @GetMapping
    @Operation(summary = "List tenant lead integrations and their health")
    public List<IntegrationConnectionResponse> list() {
        return service.listConnections(tenantId());
    }

    @GetMapping("/routing-options")
    @Operation(summary = "List tenant-scoped pipelines, pools, brokers and properties available for routing")
    public IntegrationRoutingOptionsResponse routingOptions() {
        return service.routingOptions(tenantId());
    }

    @PostMapping("/grupo-olx")
    @Operation(summary = "Create a Grupo OLX/ZAP/Viva Real inbound connection")
    public ResponseEntity<IntegrationConnectionResponse> createGrupoOlx(@Valid @RequestBody CreateGrupoOlxConnectionRequest request) {
        return ResponseEntity.status(201).body(service.createGrupoOlx(tenantId(), request));
    }

    @GetMapping("/{connectionId}/grupo-olx/feed-status")
    @Operation(summary = "Validate the Grupo OLX property feed and summarize publishable inventory")
    public GrupoOlxFeedStatusResponse grupoOlxFeedStatus(@PathVariable UUID connectionId) {
        return grupoOlxFeedService.status(tenantId(), connectionId);
    }

    @PostMapping("/meta/oauth/start")
    @Operation(summary = "Start Meta Lead Ads OAuth")
    public MetaOAuthStartResponse startMetaOAuth(HttpServletRequest request) {
        return service.startMetaOAuth(tenantId(), (UUID) request.getAttribute("userId"));
    }

    @PostMapping("/{connectionId}/meta/oauth/reconnect")
    @Operation(summary = "Restart Meta authorization for an existing tenant connection")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Authorization URL generated"),
            @ApiResponse(responseCode = "404", description = "Connection not found in tenant")})
    public MetaOAuthStartResponse reconnectMetaOAuth(@PathVariable UUID connectionId, HttpServletRequest request) {
        return service.reconnectMetaOAuth(tenantId(), (UUID) request.getAttribute("userId"), connectionId);
    }

    @GetMapping("/meta/oauth/callback")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Complete Meta Lead Ads OAuth callback")
    public ResponseEntity<Void> completeMetaOAuth(@RequestParam(required = false) String code,
                                                   @RequestParam(required = false) String state) {
        String query;
        try {
            if (state == null || state.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Meta OAuth state");
            }
            UUID connectionId = service.completeMetaOAuth(code, state);
            query = "meta=returned&connectionId=" + connectionId;
        } catch (ResponseStatusException exception) {
            if (!exception.getStatusCode().is4xxClientError()) throw exception;
            query = "meta=error";
        }
        URI redirect = URI.create(frontendUrl.replaceAll("/$", "") + "/integracoes?" + query);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, redirect.toString()).build();
    }

    @GetMapping("/{connectionId}/meta/pages")
    @Operation(summary = "List Meta pages authorized by the connected account")
    public List<MetaAssetResponse> listMetaPages(@PathVariable UUID connectionId) {
        return service.listMetaPages(tenantId(), connectionId);
    }

    @PostMapping("/{connectionId}/meta/page")
    @Operation(summary = "Select and subscribe a Meta page")
    public IntegrationConnectionResponse activateMetaPage(@PathVariable UUID connectionId,
                                                            @Valid @RequestBody ActivateMetaConnectionRequest request) {
        return service.activateMetaPage(tenantId(), connectionId, request);
    }

    @GetMapping("/{connectionId}/meta/forms")
    @Operation(summary = "List lead forms from the selected Meta page")
    public List<MetaAssetResponse> listMetaForms(@PathVariable UUID connectionId) {
        return service.listMetaForms(tenantId(), connectionId);
    }

    @GetMapping("/{connectionId}/routes")
    @Operation(summary = "List routing mappings for a connection")
    public List<IntegrationRouteResponse> listRoutes(@PathVariable UUID connectionId) {
        return service.listRoutes(tenantId(), connectionId);
    }

    @PostMapping("/{connectionId}/meta/sync")
    @Operation(summary = "Request recovery of missing Meta leads for configured forms")
    @ApiResponses({@ApiResponse(responseCode = "202", description = "Recovery requested"),
            @ApiResponse(responseCode = "404", description = "Tenant connection not found"),
            @ApiResponse(responseCode = "409", description = "Meta connection is not active")})
    public ResponseEntity<Void> requestMetaSync(@PathVariable UUID connectionId) {
        service.requestMetaSync(tenantId(), connectionId);
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/{connectionId}/routes")
    @Operation(summary = "Create or update a form/provider routing mapping")
    public IntegrationRouteResponse saveRoute(@PathVariable UUID connectionId,
                                               @Valid @RequestBody IntegrationRouteRequest request) {
        return service.saveRoute(tenantId(), connectionId, request);
    }

    @PatchMapping("/{connectionId}/status")
    @Operation(summary = "Pause or activate an integration connection")
    public IntegrationConnectionResponse updateStatus(@PathVariable UUID connectionId,
                                                        @Valid @RequestBody UpdateIntegrationStatusRequest request) {
        return service.updateStatus(tenantId(), connectionId, request.status());
    }

    @GetMapping("/events")
    @Operation(summary = "List recent integration events without exposing raw payloads")
    public Page<IntegrationEventResponse> listEvents(@RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return service.listEvents(tenantId(), page, size);
    }

    @PostMapping("/events/{eventId}/retry")
    @Operation(summary = "Retry a failed or quota-blocked integration event")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Event queued for retry"),
            @ApiResponse(responseCode = "409", description = "Event has already been finalized")
    })
    public ResponseEntity<Void> retryEvent(@PathVariable UUID eventId) {
        service.retryEvent(tenantId(), eventId);
        return ResponseEntity.noContent().build();
    }

    private UUID tenantId() {
        return UUID.fromString(TenantContext.getTenantId());
    }
}
