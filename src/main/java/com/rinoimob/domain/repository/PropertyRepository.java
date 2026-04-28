package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PropertyRepository extends JpaRepository<Property, UUID>, JpaSpecificationExecutor<Property> {

    Optional<Property> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
}
