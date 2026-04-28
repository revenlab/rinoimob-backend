package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.PropertyCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyCategoryRepository extends JpaRepository<PropertyCategory, UUID> {

    /** Returns global categories + tenant's own, ordered by position then name. */
    @Query("SELECT c FROM PropertyCategory c WHERE (c.tenantId IS NULL OR c.tenantId = :tenantId) AND c.active = true ORDER BY c.position ASC, c.name ASC")
    List<PropertyCategory> findAvailableForTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT c FROM PropertyCategory c WHERE c.tenantId = :tenantId ORDER BY c.position ASC, c.name ASC")
    List<PropertyCategory> findByTenantId(@Param("tenantId") UUID tenantId);

    Optional<PropertyCategory> findBySlugAndTenantId(String slug, UUID tenantId);

    boolean existsBySlugAndTenantId(String slug, UUID tenantId);
}
