package com.rinoimob.api.controller;

import com.rinoimob.domain.dto.CategoryResponse;
import com.rinoimob.domain.dto.CreateCategoryRequest;
import com.rinoimob.domain.dto.UpdateCategoryRequest;
import com.rinoimob.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories", description = "Property category management")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "List all categories available to the current tenant (globals + own)")
    @PreAuthorize("hasAuthority('PERMISSION_categories:read')")
    public ResponseEntity<List<CategoryResponse>> list() {
        return ResponseEntity.ok(categoryService.listAvailable());
    }

    @PostMapping
    @Operation(summary = "Create a new category for the current tenant")
    @PreAuthorize("hasAuthority('PERMISSION_categories:write')")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing tenant-owned category")
    @PreAuthorize("hasAuthority('PERMISSION_categories:write')")
    public ResponseEntity<CategoryResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a tenant-owned category")
    @PreAuthorize("hasAuthority('PERMISSION_categories:write')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
