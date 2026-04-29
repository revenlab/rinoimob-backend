package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.LeadProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeadPropertyRepository extends JpaRepository<LeadProperty, UUID> {

    List<LeadProperty> findAllByLeadIdOrderByCreatedAtAsc(UUID leadId);

    boolean existsByLeadIdAndPropertyId(UUID leadId, UUID propertyId);

    Optional<LeadProperty> findByIdAndLeadId(UUID id, UUID leadId);

    @Transactional
    void deleteByLeadIdAndPropertyId(UUID leadId, UUID propertyId);
}
