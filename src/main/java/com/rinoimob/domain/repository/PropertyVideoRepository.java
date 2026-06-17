package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.PropertyVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PropertyVideoRepository extends JpaRepository<PropertyVideo, UUID> {

    List<PropertyVideo> findByPropertyIdOrderByPositionAsc(UUID propertyId);

    Optional<PropertyVideo> findByIdAndPropertyId(UUID id, UUID propertyId);

    int countByPropertyId(UUID propertyId);
}
