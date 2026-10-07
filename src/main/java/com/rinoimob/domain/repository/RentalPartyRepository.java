package com.rinoimob.domain.repository;
import com.rinoimob.domain.entity.RentalParty;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RentalPartyRepository extends JpaRepository<RentalParty, UUID> {
    List<RentalParty> findByTenantIdAndDeletedAtIsNullOrderByNameAsc(UUID tenantId);
    Optional<RentalParty> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
}
