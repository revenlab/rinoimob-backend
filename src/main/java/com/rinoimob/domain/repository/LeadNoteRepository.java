package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.LeadNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LeadNoteRepository extends JpaRepository<LeadNote, UUID> {

    List<LeadNote> findAllByLeadIdOrderByCreatedAtDesc(UUID leadId);
}
