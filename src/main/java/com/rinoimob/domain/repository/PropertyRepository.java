package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.Property;
import com.rinoimob.domain.enums.PropertyOperation;
import com.rinoimob.domain.enums.PropertyStatus;
import com.rinoimob.domain.enums.PropertyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PropertyRepository extends JpaRepository<Property, UUID> {

    Optional<Property> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    @Query("""
        SELECT p FROM Property p
        WHERE p.tenantId = :tenantId
          AND p.deletedAt IS NULL
          AND (:status IS NULL OR p.status = :status)
          AND (:operation IS NULL OR p.operation = :operation)
          AND (:propertyType IS NULL OR p.propertyType = :propertyType)
          AND (:minPrice IS NULL OR p.price >= :minPrice)
          AND (:maxPrice IS NULL OR p.price <= :maxPrice)
          AND (:bedrooms IS NULL OR p.bedrooms >= :bedrooms)
          AND (:city IS NULL OR LOWER(p.addressCity) LIKE LOWER(CONCAT('%', :city, '%')))
        """)
    Page<Property> findWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("status") PropertyStatus status,
            @Param("operation") PropertyOperation operation,
            @Param("propertyType") PropertyType propertyType,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("bedrooms") Integer bedrooms,
            @Param("city") String city,
            Pageable pageable);
}
