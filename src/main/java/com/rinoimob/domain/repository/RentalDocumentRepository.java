package com.rinoimob.domain.repository;
import com.rinoimob.domain.entity.RentalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RentalDocumentRepository extends JpaRepository<RentalDocument, UUID> {
    List<RentalDocument> findByTenantIdAndContractIdOrderByCreatedAtDesc(UUID tenantId, UUID contractId);
    Optional<RentalDocument> findByIdAndTenantIdAndContractId(UUID id, UUID tenantId, UUID contractId);
}
