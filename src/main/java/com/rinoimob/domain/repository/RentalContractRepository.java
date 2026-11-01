package com.rinoimob.domain.repository;
import com.rinoimob.domain.entity.RentalContract;
import com.rinoimob.domain.enums.RentalEnums.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RentalContractRepository extends JpaRepository<RentalContract, UUID> {
    List<RentalContract> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID tenantId);
    List<RentalContract> findByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, ContractStatus status);
    List<RentalContract> findByStatusAndDeletedAtIsNull(ContractStatus status);
    Optional<RentalContract> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
    boolean existsByTenantIdAndPropertyIdAndStatusAndDeletedAtIsNull(UUID tenantId, UUID propertyId, ContractStatus status);
    long countByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, ContractStatus status);
}
