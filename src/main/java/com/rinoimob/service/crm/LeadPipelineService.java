package com.rinoimob.service.crm;

import com.rinoimob.domain.dto.*;
import com.rinoimob.domain.entity.*;
import com.rinoimob.domain.enums.LeadPipelineStageKind;
import com.rinoimob.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LeadPipelineService {
    private final LeadPipelineRepository pipelineRepository;
    private final LeadPipelineStageRepository stageRepository;
    private final LeadPipelineSourceRepository sourceRepository;

    @Transactional(readOnly = true)
    public List<LeadPipelineResponse> list(UUID tenantId) {
        return pipelineRepository.findByTenantIdAndArchivedAtIsNullOrderByCreatedAtAsc(tenantId).stream().map(this::response).toList();
    }

    @Transactional
    public LeadPipelineResponse create(UUID tenantId, CreateLeadPipelineRequest request) {
        LeadPipeline pipeline = new LeadPipeline();
        pipeline.setTenantId(tenantId);
        pipeline.setName(requiredName(request.name()));
        pipeline.setDescription(request.description());
        pipeline = pipelineRepository.save(pipeline);
        replaceStages(pipeline.getId(), request.stages().stream().map(CreateLeadPipelineRequest.OpenStageRequest::name).toList());
        replaceSources(tenantId, pipeline.getId(), request.sources());
        return response(pipeline);
    }

    @Transactional
    public LeadPipelineResponse update(UUID tenantId, UUID id, UpdateLeadPipelineRequest request) {
        LeadPipeline pipeline = owned(tenantId, id);
        if (request.name() != null) pipeline.setName(requiredName(request.name()));
        if (request.description() != null) pipeline.setDescription(request.description());
        pipelineRepository.save(pipeline);
        if (request.stages() != null) replaceStages(id, request.stages().stream().map(UpdateLeadPipelineRequest.OpenStageRequest::name).toList());
        if (request.sources() != null) replaceSources(tenantId, id, request.sources());
        return response(pipeline);
    }

    @Transactional
    public void archive(UUID tenantId, UUID id) {
        LeadPipeline pipeline = owned(tenantId, id);
        if (pipeline.isDefaultPipeline()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Default pipeline cannot be archived");
        pipeline.setArchivedAt(LocalDateTime.now());
        pipelineRepository.save(pipeline);
    }

    @Transactional(readOnly = true)
    public LeadPipelineStage requireStage(UUID tenantId, UUID pipelineId, UUID stageId) {
        owned(tenantId, pipelineId);
        return stageRepository.findByIdAndPipelineId(stageId, pipelineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stage does not belong to pipeline"));
    }

    @Transactional(readOnly = true)
    public LeadPipeline defaultPipeline(UUID tenantId) {
        return pipelineRepository.findByTenantIdAndDefaultPipelineTrueAndArchivedAtIsNull(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Default pipeline missing"));
    }

    @Transactional(readOnly = true)
    public LeadPipelineStage resolveInitialStage(UUID tenantId, String source) {
        List<LeadPipeline> pipelines = pipelineRepository.findByTenantIdAndArchivedAtIsNullOrderByCreatedAtAsc(tenantId);
        Set<UUID> ids = pipelines.stream().map(LeadPipeline::getId).collect(java.util.stream.Collectors.toSet());
        LeadPipeline pipeline = sourceRepository.findBySourceAndPipelineIdIn(source, ids).map(s -> pipelines.stream().filter(p -> p.getId().equals(s.getPipelineId())).findFirst().orElseThrow())
                .orElseGet(() -> defaultPipeline(tenantId));
        return stageRepository.findFirstByPipelineIdAndKindOrderByPositionAsc(pipeline.getId(), LeadPipelineStageKind.OPEN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Pipeline has no open stage"));
    }

    private void replaceStages(UUID pipelineId, List<String> requestedOpenStages) {
        if (requestedOpenStages == null || requestedOpenStages.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one open stage is required");
        List<LeadPipelineStage> existing = stageRepository.findByPipelineIdOrderByPositionAsc(pipelineId);
        existing.stream().filter(s -> s.getKind() == LeadPipelineStageKind.OPEN).forEach(stageRepository::delete);
        int position = 10;
        for (String name : requestedOpenStages) {
            LeadPipelineStage stage = new LeadPipelineStage(); stage.setPipelineId(pipelineId); stage.setName(requiredName(name)); stage.setPosition(position); stage.setKind(LeadPipelineStageKind.OPEN); stageRepository.save(stage); position += 10;
        }
        ensureTerminal(pipelineId, LeadPipelineStageKind.WON, "Ganho", 90);
        ensureTerminal(pipelineId, LeadPipelineStageKind.LOST, "Perdido", 100);
    }

    private void ensureTerminal(UUID pipelineId, LeadPipelineStageKind kind, String name, int position) {
        if (stageRepository.findFirstByPipelineIdAndKindOrderByPositionAsc(pipelineId, kind).isEmpty()) {
            LeadPipelineStage stage = new LeadPipelineStage(); stage.setPipelineId(pipelineId); stage.setName(name); stage.setPosition(position); stage.setKind(kind); stageRepository.save(stage);
        }
    }

    private void replaceSources(UUID tenantId, UUID pipelineId, List<String> sources) {
        List<String> normalized = sources == null ? List.of() : sources.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).distinct().toList();
        List<LeadPipeline> all = pipelineRepository.findByTenantIdAndArchivedAtIsNullOrderByCreatedAtAsc(tenantId);
        Set<UUID> otherIds = all.stream().map(LeadPipeline::getId).filter(id -> !id.equals(pipelineId)).collect(java.util.stream.Collectors.toSet());
        for (String source : normalized) if (!otherIds.isEmpty() && sourceRepository.findBySourceAndPipelineIdIn(source, otherIds).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Source already belongs to another pipeline: " + source);
        sourceRepository.deleteByPipelineId(pipelineId);
        for (String source : normalized) { LeadPipelineSource row = new LeadPipelineSource(); row.setPipelineId(pipelineId); row.setSource(source); sourceRepository.save(row); }
    }

    private LeadPipeline owned(UUID tenantId, UUID id) { return pipelineRepository.findByIdAndTenantId(id, tenantId).filter(p -> p.getArchivedAt() == null).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pipeline not found")); }
    private String requiredName(String value) { if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required"); return value.trim(); }
    private LeadPipelineResponse response(LeadPipeline pipeline) { return new LeadPipelineResponse(pipeline.getId(), pipeline.getName(), pipeline.getDescription(), pipeline.isDefaultPipeline(), pipeline.getArchivedAt() != null, stageRepository.findByPipelineIdOrderByPositionAsc(pipeline.getId()).stream().map(s -> new LeadPipelineStageResponse(s.getId(), s.getName(), s.getPosition(), s.getKind())).toList(), sourceRepository.findByPipelineId(pipeline.getId()).stream().map(LeadPipelineSource::getSource).toList()); }
}
