package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.blog.BlogPostResponse;
import com.rinoimob.domain.dto.blog.CreateBlogPostRequest;
import com.rinoimob.domain.dto.blog.UpdateBlogPostRequest;
import com.rinoimob.domain.dto.blog.UpdateBlogPostStatusRequest;
import com.rinoimob.service.BlogPostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/blog-posts")
@RequiredArgsConstructor
public class BlogPostController {

    private final BlogPostService blogPostService;

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    public ResponseEntity<Page<BlogPostResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(blogPostService.list(tenantId, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    public ResponseEntity<BlogPostResponse> get(@PathVariable UUID id) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(blogPostService.get(tenantId, id));
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    public ResponseEntity<BlogPostResponse> create(@Valid @RequestBody CreateBlogPostRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(blogPostService.create(tenantId, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    public ResponseEntity<BlogPostResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBlogPostRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(blogPostService.update(tenantId, id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    public ResponseEntity<BlogPostResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBlogPostStatusRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(blogPostService.updateStatus(tenantId, id, request.status()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        blogPostService.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
