package com.rinoimob.domain.repository;
import com.rinoimob.domain.entity.RentalContractParty;
import com.rinoimob.domain.enums.RentalEnums.PartyRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RentalContractPartyRepository extends JpaRepository<RentalContractParty, UUID> {
    List<RentalContractParty> findByTenantIdAndContractId(UUID tenantId, UUID contractId);
    List<RentalContractParty> findByTenantIdAndContractIdAndRole(UUID tenantId, UUID contractId, PartyRole role);
    void deleteByTenantIdAndContractId(UUID tenantId, UUID contractId);
}
