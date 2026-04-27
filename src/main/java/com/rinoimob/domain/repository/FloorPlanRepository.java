package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.FloorPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FloorPlanRepository extends JpaRepository<FloorPlan, UUID> {

    List<FloorPlan> findByPropertyIdOrderByCreatedAtAsc(UUID propertyId);

    Optional<FloorPlan> findByIdAndPropertyId(UUID id, UUID propertyId);
}
