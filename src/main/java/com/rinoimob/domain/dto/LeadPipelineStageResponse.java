package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.LeadPipelineStageKind;
import java.util.UUID;

public record LeadPipelineStageResponse(UUID id, String name, int position, LeadPipelineStageKind kind) {}
