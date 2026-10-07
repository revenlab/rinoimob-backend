package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.LeadAcquisition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LeadAcquisitionRepository extends JpaRepository<LeadAcquisition, UUID> {
    Optional<LeadAcquisition> findByLeadIdAndTenantId(UUID leadId, UUID tenantId);
}
