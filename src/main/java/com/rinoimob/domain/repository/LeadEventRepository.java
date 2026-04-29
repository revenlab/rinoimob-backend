package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.LeadEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LeadEventRepository extends JpaRepository<LeadEvent, UUID> {

    List<LeadEvent> findAllByLeadIdOrderByCreatedAtDesc(UUID leadId);
}
