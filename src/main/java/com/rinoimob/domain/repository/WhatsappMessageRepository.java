package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.WhatsappMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WhatsappMessageRepository extends JpaRepository<WhatsappMessage, UUID> {

    List<WhatsappMessage> findByLeadIdOrderByCreatedAtAsc(UUID leadId);

    List<WhatsappMessage> findByTenantIdAndLeadIdIsNullOrderByCreatedAtDesc(UUID tenantId);
}
