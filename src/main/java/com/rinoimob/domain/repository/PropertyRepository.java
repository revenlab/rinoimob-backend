package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PropertyRepository extends JpaRepository<Property, UUID> {

    List<Property> findByTenantId(UUID tenantId);

    List<Property> findByTenantIdAndActive(UUID tenantId, Boolean active);

    List<Property> findByTenantIdAndOwnerId(UUID tenantId, UUID ownerId);

}
