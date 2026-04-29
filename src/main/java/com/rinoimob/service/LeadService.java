package com.rinoimob.service;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.*;
import com.rinoimob.domain.entity.Lead;
import com.rinoimob.domain.entity.LeadEvent;
import com.rinoimob.domain.entity.LeadNote;
import com.rinoimob.domain.entity.LeadProperty;
import com.rinoimob.domain.entity.Property;
import com.rinoimob.domain.entity.PropertyPhoto;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.enums.InterestLevel;
import com.rinoimob.domain.enums.LeadEventType;
import com.rinoimob.domain.enums.LeadStatus;
import com.rinoimob.domain.repository.LeadEventRepository;
import com.rinoimob.domain.repository.LeadNoteRepository;
import com.rinoimob.domain.repository.LeadPropertyRepository;
import com.rinoimob.domain.repository.LeadRepository;
import com.rinoimob.domain.repository.PropertyRepository;
import com.rinoimob.domain.repository.UserRepository;
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
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadNoteRepository leadNoteRepository;
    private final LeadEventRepository leadEventRepository;
    private final UserRepository userRepository;
    private final LeadPropertyRepository leadPropertyRepository;
    private final PropertyRepository propertyRepository;

    @Transactional(readOnly = true)
    public Page<LeadResponse> list(UUID tenantId, LeadStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (status != null) {
            return leadRepository.findAllByTenantIdAndStatusAndDeletedAtIsNull(tenantId, status, pageable)
                    .map(l -> toResponse(l, List.of(), resolveUserName(l.getAssignedTo()), List.of()));
        }
        return leadRepository.findAllByTenantIdAndDeletedAtIsNull(tenantId, pageable)
                .map(l -> toResponse(l, List.of(), resolveUserName(l.getAssignedTo()), List.of()));
    }

    @Transactional(readOnly = true)
    public LeadResponse get(UUID tenantId, UUID id) {
        Lead lead = findOwned(id, tenantId);
        List<LeadNote> notes = leadNoteRepository.findAllByLeadIdOrderByCreatedAtDesc(id);
        List<LeadProperty> leadProps = leadPropertyRepository.findAllByLeadIdOrderByCreatedAtAsc(id);
        List<LeadPropertyResponse> propResponses = leadProps.stream().map(this::toLeadPropertyResponse).toList();
        return toResponse(lead, notes, resolveUserName(lead.getAssignedTo()), propResponses);
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
        logEvent(lead.getId(), null, LeadEventType.CREATED, "Lead criado via " + lead.getSource());
        if (req.propertyId() != null && !leadPropertyRepository.existsByLeadIdAndPropertyId(lead.getId(), req.propertyId())) {
            LeadProperty lp = new LeadProperty();
            lp.setLeadId(lead.getId());
            lp.setPropertyId(req.propertyId());
            lp.setInterestLevel(InterestLevel.UNDEFINED);
            leadPropertyRepository.save(lp);
        }
        log.info("Lead created id={} tenant={}", lead.getId(), tenantId);
        return toResponse(lead, List.of(), null, List.of());
    }

    @Transactional
    public LeadResponse update(UUID tenantId, UUID id, UpdateLeadRequest req) {
        Lead lead = findOwned(id, tenantId);
        if (req.name() != null) lead.setName(req.name());
        if (req.email() != null) lead.setEmail(req.email());
        if (req.phone() != null) lead.setPhone(req.phone());
        if (req.message() != null) lead.setMessage(req.message());
        if (req.status() != null && !req.status().equals(lead.getStatus())) {
            LeadStatus oldStatus = lead.getStatus();
            lead.setStatus(req.status());
            logEvent(lead.getId(), null, LeadEventType.STATUS_CHANGED,
                    "Status alterado de " + oldStatus + " para " + req.status());
        }
        if (req.assignedTo() != null && !req.assignedTo().equals(lead.getAssignedTo())) {
            lead.setAssignedTo(req.assignedTo());
            String userName = resolveUserName(req.assignedTo());
            logEvent(lead.getId(), null, LeadEventType.ASSIGNED,
                    "Atribuído a " + (userName != null ? userName : req.assignedTo()));
        }
        lead = leadRepository.save(lead);
        log.info("Lead updated id={}", id);
        List<LeadNote> notes = leadNoteRepository.findAllByLeadIdOrderByCreatedAtDesc(id);
        List<LeadProperty> leadProps = leadPropertyRepository.findAllByLeadIdOrderByCreatedAtAsc(id);
        List<LeadPropertyResponse> propResponses = leadProps.stream().map(this::toLeadPropertyResponse).toList();
        return toResponse(lead, notes, resolveUserName(lead.getAssignedTo()), propResponses);
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
        logEvent(leadId, userId, LeadEventType.NOTE_ADDED, "Nota adicionada");
        log.info("Note added to lead={} by user={}", leadId, userId);
        return toNoteResponse(note);
    }

    @Transactional(readOnly = true)
    public List<LeadEventResponse> getEvents(UUID tenantId, UUID leadId) {
        findOwned(leadId, tenantId);
        return leadEventRepository.findAllByLeadIdOrderByCreatedAtDesc(leadId)
                .stream().map(this::toEventResponse).toList();
    }

    @Transactional
    public LeadPropertyResponse addProperty(UUID tenantId, UUID leadId, AddLeadPropertyRequest req) {
        findOwned(leadId, tenantId);
        if (leadPropertyRepository.existsByLeadIdAndPropertyId(leadId, req.propertyId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Property already linked to this lead");
        }
        LeadProperty lp = new LeadProperty();
        lp.setLeadId(leadId);
        lp.setPropertyId(req.propertyId());
        lp.setInterestLevel(req.interestLevel() != null ? req.interestLevel() : InterestLevel.UNDEFINED);
        lp = leadPropertyRepository.save(lp);
        logEvent(leadId, null, LeadEventType.PROPERTY_LINKED, "Imóvel vinculado: " + req.propertyId());
        return toLeadPropertyResponse(lp);
    }

    @Transactional
    public LeadPropertyResponse updatePropertyInterest(UUID tenantId, UUID leadId, UUID linkId, UpdateLeadPropertyRequest req) {
        findOwned(leadId, tenantId);
        LeadProperty lp = leadPropertyRepository.findByIdAndLeadId(linkId, leadId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Link not found"));
        lp.setInterestLevel(req.interestLevel());
        lp = leadPropertyRepository.save(lp);
        return toLeadPropertyResponse(lp);
    }

    @Transactional
    public void removeProperty(UUID tenantId, UUID leadId, UUID linkId) {
        findOwned(leadId, tenantId);
        LeadProperty lp = leadPropertyRepository.findByIdAndLeadId(linkId, leadId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Link not found"));
        leadPropertyRepository.delete(lp);
        logEvent(leadId, null, LeadEventType.PROPERTY_UNLINKED, "Imóvel desvinculado");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Lead findOwned(UUID id, UUID tenantId) {
        return leadRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
    }

    private void logEvent(UUID leadId, UUID userId, LeadEventType type, String description) {
        LeadEvent event = new LeadEvent();
        event.setLeadId(leadId);
        event.setUserId(userId);
        event.setEventType(type);
        event.setDescription(description);
        leadEventRepository.save(event);
    }

    private String resolveUserName(UUID userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(u -> {
            if (u.getFirstName() != null && !u.getFirstName().isBlank()) {
                return u.getLastName() != null ? u.getFirstName() + " " + u.getLastName() : u.getFirstName();
            }
            return u.getEmail();
        }).orElse(null);
    }

    private LeadPropertyResponse toLeadPropertyResponse(LeadProperty lp) {
        Optional<Property> propOpt = propertyRepository.findByIdWithPhotos(lp.getPropertyId());
        if (propOpt.isEmpty()) {
            return new LeadPropertyResponse(lp.getId(), lp.getLeadId(), lp.getPropertyId(),
                    lp.getInterestLevel(), lp.getCreatedAt(), null, null, null, null, null, null, null);
        }
        Property p = propOpt.get();
        String coverUrl = p.getPhotos().stream()
                .filter(ph -> Boolean.TRUE.equals(ph.getIsCover()))
                .findFirst().map(PropertyPhoto::getUrl).orElse(null);
        return new LeadPropertyResponse(lp.getId(), lp.getLeadId(), lp.getPropertyId(),
                lp.getInterestLevel(), lp.getCreatedAt(),
                p.getTitle(), p.getOperation().name(), p.getPrice(), p.getCurrency(),
                p.getAddressCity(), p.getAddressState(), coverUrl);
    }

    public LeadResponse toResponse(Lead l, List<LeadNote> notes, String assignedToName, List<LeadPropertyResponse> properties) {
        return new LeadResponse(
                l.getId(), l.getTenantId(), l.getPropertyId(),
                l.getName(), l.getEmail(), l.getPhone(), l.getMessage(),
                l.getStatus(), l.getSource(), l.getAssignedTo(), assignedToName,
                l.getCreatedAt(), l.getUpdatedAt(),
                notes.stream().map(this::toNoteResponse).toList(),
                properties
        );
    }

    private LeadNoteResponse toNoteResponse(LeadNote n) {
        return new LeadNoteResponse(n.getId(), n.getLeadId(), n.getUserId(), n.getContent(), n.getCreatedAt());
    }

    private LeadEventResponse toEventResponse(LeadEvent e) {
        return new LeadEventResponse(e.getId(), e.getLeadId(), e.getUserId(), e.getEventType(), e.getDescription(), e.getCreatedAt());
    }
}
