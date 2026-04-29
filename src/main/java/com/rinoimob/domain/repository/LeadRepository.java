package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.Lead;
import com.rinoimob.domain.enums.LeadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeadRepository extends JpaRepository<Lead, UUID> {

    Page<Lead> findAllByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

    Page<Lead> findAllByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, LeadStatus status, Pageable pageable);

    Optional<Lead> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    List<Lead> findAllByTenantIdAndDeletedAtIsNullAndStatus(UUID tenantId, LeadStatus status);
}
