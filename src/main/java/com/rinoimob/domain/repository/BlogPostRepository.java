package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.BlogPost;
import com.rinoimob.domain.enums.BlogPostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BlogPostRepository extends JpaRepository<BlogPost, UUID> {

    Page<BlogPost> findAllByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

    Page<BlogPost> findAllByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, BlogPostStatus status, Pageable pageable);

    Optional<BlogPost> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    Optional<BlogPost> findByTenantIdAndSlugAndStatusAndDeletedAtIsNull(UUID tenantId, String slug, BlogPostStatus status);

    boolean existsByTenantIdAndSlugAndDeletedAtIsNull(UUID tenantId, String slug);

    boolean existsByTenantIdAndSlugAndDeletedAtIsNullAndIdNot(UUID tenantId, String slug, UUID id);
}
