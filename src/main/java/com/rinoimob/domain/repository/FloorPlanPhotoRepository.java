package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.FloorPlanPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FloorPlanPhotoRepository extends JpaRepository<FloorPlanPhoto, UUID> {

    List<FloorPlanPhoto> findByFloorPlanIdOrderByPositionAsc(UUID floorPlanId);

    int countByFloorPlanId(UUID floorPlanId);
}
