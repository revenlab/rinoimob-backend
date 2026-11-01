package com.rinoimob.domain.repository;
import com.rinoimob.domain.entity.RentalChargeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RentalChargeItemRepository extends JpaRepository<RentalChargeItem, UUID> {
    List<RentalChargeItem> findByTenantIdAndChargeIdOrderByCreatedAtAsc(UUID tenantId, UUID chargeId);
}
