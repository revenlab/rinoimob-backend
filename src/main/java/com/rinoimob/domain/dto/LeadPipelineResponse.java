package com.rinoimob.domain.dto;

import java.util.List;
import java.util.UUID;

public record LeadPipelineResponse(UUID id, String name, String description, boolean defaultPipeline,
                                   boolean archived, List<LeadPipelineStageResponse> stages, List<String> sources) {}
