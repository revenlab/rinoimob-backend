package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.PropertyPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PropertyPhotoRepository extends JpaRepository<PropertyPhoto, UUID> {

    List<PropertyPhoto> findByPropertyIdOrderByPositionAsc(UUID propertyId);

    Optional<PropertyPhoto> findByIdAndPropertyId(UUID id, UUID propertyId);

    int countByPropertyId(UUID propertyId);
}
