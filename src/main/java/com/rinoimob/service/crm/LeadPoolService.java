package com.rinoimob.service.crm;

import com.rinoimob.domain.dto.CreateLeadPoolRequest;
import com.rinoimob.domain.dto.LeadPoolResponse;
import com.rinoimob.domain.dto.UpdateLeadPoolRequest;
import com.rinoimob.domain.entity.LeadPool;
import com.rinoimob.domain.repository.LeadPoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class LeadPoolService {

    private final LeadPoolRepository leadPoolRepository;

    @Transactional(readOnly = true)
    public List<LeadPoolResponse> list(UUID tenantId) {
        return leadPoolRepository.findByTenantId(tenantId).stream()
                .map(p -> new LeadPoolResponse(p.getId(), p.getTenantId(), p.getName(), p.getDescription(), p.getCreatedAt()))
                .toList();
    }

    @Transactional
    public LeadPoolResponse create(UUID tenantId, CreateLeadPoolRequest req) {
        LeadPool p = new LeadPool();
        p.setTenantId(tenantId);
        p.setName(req.name());
        p.setDescription(req.description());
        p = leadPoolRepository.save(p);
        return new LeadPoolResponse(p.getId(), p.getTenantId(), p.getName(), p.getDescription(), p.getCreatedAt());
    }

    @Transactional
    public LeadPoolResponse update(UUID tenantId, UUID id, UpdateLeadPoolRequest req) {
        LeadPool p = leadPoolRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Lead pool not found"));
        if (req.name() != null) p.setName(req.name());
        if (req.description() != null) p.setDescription(req.description());
        p = leadPoolRepository.save(p);
        return new LeadPoolResponse(p.getId(), p.getTenantId(), p.getName(), p.getDescription(), p.getCreatedAt());
    }

    @Transactional
    public void delete(UUID tenantId, UUID id) {
        LeadPool p = leadPoolRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Lead pool not found"));
        leadPoolRepository.deleteByIdAndTenantId(id, tenantId);
    }
}
