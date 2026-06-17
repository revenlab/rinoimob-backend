package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.TenantPropertyType;
import com.rinoimob.domain.enums.PropertyType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantPropertyTypeRepository extends JpaRepository<TenantPropertyType, UUID> {

    List<TenantPropertyType> findByTenantIdOrderByPositionAscLabelAsc(UUID tenantId);

    List<TenantPropertyType> findByTenantIdAndActiveTrueOrderByPositionAscLabelAsc(UUID tenantId);

    Optional<TenantPropertyType> findByTenantIdAndCode(UUID tenantId, PropertyType code);

    boolean existsByTenantIdAndCode(UUID tenantId, PropertyType code);
}
