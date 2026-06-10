package com.rinoimob.api.controller;

import com.rinoimob.domain.dto.CreateEmailSenderConfigRequest;
import com.rinoimob.domain.dto.EmailSenderConfigResponse;
import com.rinoimob.domain.dto.UpdateEmailSenderConfigRequest;
import com.rinoimob.service.core.EmailSenderConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/email-sender-configs")
@RequiredArgsConstructor
public class EmailSenderConfigController {

    private final EmailSenderConfigService service;

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_settings:manage')")
    @Operation(summary = "List all email sender configurations for the current tenant")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Configurations retrieved"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<List<EmailSenderConfigResponse>> list() {
        return ResponseEntity.ok(service.listForTenant());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_settings:manage')")
    @Operation(summary = "Get an email sender configuration by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Configuration found"),
        @ApiResponse(responseCode = "404", description = "Configuration not found"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<EmailSenderConfigResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_settings:manage')")
    @Operation(summary = "Create a new email sender configuration")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Configuration created"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<EmailSenderConfigResponse> create(
            @Valid @RequestBody CreateEmailSenderConfigRequest req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_settings:manage')")
    @Operation(summary = "Update an existing email sender configuration")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Configuration updated"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "404", description = "Configuration not found"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<EmailSenderConfigResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEmailSenderConfigRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_settings:manage')")
    @Operation(summary = "Delete an email sender configuration")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Configuration deleted"),
        @ApiResponse(responseCode = "404", description = "Configuration not found"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
