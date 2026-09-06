package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface PropertyRepository extends JpaRepository<Property, UUID>, JpaSpecificationExecutor<Property> {

    Optional<Property> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    @Query("SELECT p FROM Property p LEFT JOIN FETCH p.photos WHERE p.id = :id")
    Optional<Property> findByIdWithPhotos(@Param("id") UUID id);

    boolean existsByTenantIdAndSlugAndDeletedAtIsNull(UUID tenantId, String slug);

    boolean existsByTenantIdAndReferenceCodeAndDeletedAtIsNull(UUID tenantId, String referenceCode);

    long countByTenantIdAndDeletedAtIsNull(UUID tenantId);

    @Query(value = "SELECT DISTINCT p FROM Property p LEFT JOIN p.brokers b WHERE p.tenantId = :tenantId AND p.status = com.rinoimob.domain.enums.PropertyStatus.ACTIVE AND p.deletedAt IS NULL AND (p.availableToAllBrokers = true OR b.id = :brokerId)", countQuery = "SELECT COUNT(DISTINCT p) FROM Property p LEFT JOIN p.brokers b WHERE p.tenantId = :tenantId AND p.status = com.rinoimob.domain.enums.PropertyStatus.ACTIVE AND p.deletedAt IS NULL AND (p.availableToAllBrokers = true OR b.id = :brokerId)")
    Page<Property> findPublicByBroker(@Param("tenantId") UUID tenantId, @Param("brokerId") UUID brokerId, Pageable pageable);
}
