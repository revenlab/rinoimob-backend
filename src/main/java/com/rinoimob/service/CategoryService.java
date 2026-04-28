package com.rinoimob.service;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.CategoryResponse;
import com.rinoimob.domain.dto.CreateCategoryRequest;
import com.rinoimob.domain.dto.UpdateCategoryRequest;
import com.rinoimob.domain.entity.PropertyCategory;
import com.rinoimob.domain.repository.PropertyCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final PropertyCategoryRepository categoryRepository;

    /** Returns global categories + current tenant's own categories. */
    public List<CategoryResponse> listAvailable() {
        UUID tenantId = currentTenantId();
        return categoryRepository.findAvailableForTenant(tenantId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        UUID tenantId = currentTenantId();

        if (categoryRepository.existsBySlugAndTenantId(request.slug(), tenantId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A category with this slug already exists");
        }

        PropertyCategory category = new PropertyCategory();
        category.setTenantId(tenantId);
        category.setName(request.name());
        category.setSlug(request.slug());
        category.setColor(request.color());
        category.setPosition(request.position() != null ? request.position() : 0);
        category.setActive(true);

        PropertyCategory saved = categoryRepository.save(category);
        log.info("Category created id={} tenant={}", saved.getId(), tenantId);
        return toResponse(saved);
    }

    @Transactional
    public CategoryResponse update(UUID id, UpdateCategoryRequest request) {
        UUID tenantId = currentTenantId();
        PropertyCategory category = getOwnedCategory(id, tenantId);

        category.setName(request.name());
        if (request.color() != null) category.setColor(request.color());
        if (request.position() != null) category.setPosition(request.position());
        if (request.active() != null) category.setActive(request.active());

        PropertyCategory saved = categoryRepository.save(category);
        log.info("Category updated id={} tenant={}", id, tenantId);
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = currentTenantId();
        PropertyCategory category = getOwnedCategory(id, tenantId);
        categoryRepository.delete(category);
        log.info("Category deleted id={} tenant={}", id, tenantId);
    }

    private PropertyCategory getOwnedCategory(UUID id, UUID tenantId) {
        PropertyCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        if (category.isGlobal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot modify global categories");
        }
        if (!tenantId.equals(category.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Category does not belong to this tenant");
        }
        return category;
    }

    private UUID currentTenantId() {
        String id = TenantContext.getTenantId();
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No tenant context");
        return UUID.fromString(id);
    }

    public CategoryResponse toResponse(PropertyCategory c) {
        return new CategoryResponse(c.getId(), c.getTenantId(), c.getName(), c.getSlug(),
                c.getColor(), c.getActive(), c.getPosition(), c.isGlobal());
    }
}
