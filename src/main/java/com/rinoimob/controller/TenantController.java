package com.rinoimob.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tenants")
@Tag(name = "Tenants", description = "Tenant management endpoints")
public class TenantController {

    @GetMapping
    @Operation(summary = "Get all tenants", description = "Retrieve a list of all tenants in the system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list of tenants"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getAllTenants() {
        return ResponseEntity.ok(Map.of("message", "Get all tenants"));
    }

    @GetMapping("/{tenantId}")
    @Operation(summary = "Get tenant by ID", description = "Retrieve a specific tenant by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tenant found"),
            @ApiResponse(responseCode = "404", description = "Tenant not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getTenantById(@PathVariable String tenantId) {
        return ResponseEntity.ok(Map.of("tenantId", tenantId));
    }

    @PostMapping
    @Operation(summary = "Create a new tenant", description = "Create a new tenant in the system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Tenant created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> createTenant(@RequestBody Map<String, Object> tenantData) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", "tenant-" + System.currentTimeMillis());
        response.put("name", tenantData.get("name"));
        response.put("createdAt", System.currentTimeMillis());
        return ResponseEntity.status(201).body(response);
    }
}
