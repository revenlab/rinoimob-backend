package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyRepository extends JpaRepository<Property, String> {

    List<Property> findByTenantId(String tenantId);

    List<Property> findByTenantIdAndActive(String tenantId, Boolean active);

    List<Property> findByOwnerId(String ownerId);

}
