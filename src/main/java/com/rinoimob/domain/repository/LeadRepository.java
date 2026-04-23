package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeadRepository extends JpaRepository<Lead, String> {

    List<Lead> findByTenantId(String tenantId);

    List<Lead> findByTenantIdAndStatus(String tenantId, String status);

    List<Lead> findByAssignedTo(String assignedTo);

}
