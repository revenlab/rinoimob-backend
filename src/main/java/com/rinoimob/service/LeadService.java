package com.rinoimob.service;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.*;
import com.rinoimob.domain.entity.Lead;
import com.rinoimob.domain.entity.LeadNote;
import com.rinoimob.domain.enums.LeadStatus;
import com.rinoimob.domain.repository.LeadNoteRepository;
import com.rinoimob.domain.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadNoteRepository leadNoteRepository;

    @Transactional(readOnly = true)
    public Page<LeadResponse> list(UUID tenantId, LeadStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (status != null) {
            return leadRepository.findAllByTenantIdAndStatusAndDeletedAtIsNull(tenantId, status, pageable)
                    .map(l -> toResponse(l, List.of()));
        }
        return leadRepository.findAllByTenantIdAndDeletedAtIsNull(tenantId, pageable)
                .map(l -> toResponse(l, List.of()));
    }

    @Transactional(readOnly = true)
    public LeadResponse get(UUID tenantId, UUID id) {
        Lead lead = findOwned(id, tenantId);
        List<LeadNote> notes = leadNoteRepository.findAllByLeadIdOrderByCreatedAtDesc(id);
        return toResponse(lead, notes);
    }

    @Transactional
    public LeadResponse create(UUID tenantId, CreateLeadRequest req) {
        Lead lead = new Lead();
        lead.setTenantId(tenantId);
        lead.setName(req.name());
        lead.setEmail(req.email());
        lead.setPhone(req.phone());
        lead.setMessage(req.message());
        lead.setPropertyId(req.propertyId());
        lead.setSource(req.source() != null ? req.source() : "MANUAL");
        lead.setStatus(LeadStatus.NEW);
        lead = leadRepository.save(lead);
        log.info("Lead created id={} tenant={}", lead.getId(), tenantId);
        return toResponse(lead, List.of());
    }

    @Transactional
    public LeadResponse update(UUID tenantId, UUID id, UpdateLeadRequest req) {
        Lead lead = findOwned(id, tenantId);
        if (req.name() != null) lead.setName(req.name());
        if (req.email() != null) lead.setEmail(req.email());
        if (req.phone() != null) lead.setPhone(req.phone());
        if (req.message() != null) lead.setMessage(req.message());
        if (req.status() != null) lead.setStatus(req.status());
        if (req.assignedTo() != null) lead.setAssignedTo(req.assignedTo());
        lead = leadRepository.save(lead);
        log.info("Lead updated id={}", id);
        List<LeadNote> notes = leadNoteRepository.findAllByLeadIdOrderByCreatedAtDesc(id);
        return toResponse(lead, notes);
    }

    @Transactional
    public void delete(UUID tenantId, UUID id) {
        Lead lead = findOwned(id, tenantId);
        lead.setDeletedAt(LocalDateTime.now());
        leadRepository.save(lead);
        log.info("Lead soft-deleted id={}", id);
    }

    @Transactional
    public LeadNoteResponse addNote(UUID tenantId, UUID leadId, UUID userId, LeadNoteRequest req) {
        findOwned(leadId, tenantId);
        LeadNote note = new LeadNote();
        note.setLeadId(leadId);
        note.setUserId(userId);
        note.setContent(req.content());
        note = leadNoteRepository.save(note);
        log.info("Note added to lead={} by user={}", leadId, userId);
        return toNoteResponse(note);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Lead findOwned(UUID id, UUID tenantId) {
        return leadRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
    }

    public LeadResponse toResponse(Lead l, List<LeadNote> notes) {
        return new LeadResponse(
                l.getId(), l.getTenantId(), l.getPropertyId(),
                l.getName(), l.getEmail(), l.getPhone(), l.getMessage(),
                l.getStatus(), l.getSource(), l.getAssignedTo(),
                l.getCreatedAt(), l.getUpdatedAt(),
                notes.stream().map(this::toNoteResponse).toList()
        );
    }

    private LeadNoteResponse toNoteResponse(LeadNote n) {
        return new LeadNoteResponse(n.getId(), n.getLeadId(), n.getUserId(), n.getContent(), n.getCreatedAt());
    }
}
