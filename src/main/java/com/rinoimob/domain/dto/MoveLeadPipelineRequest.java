package com.rinoimob.domain.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MoveLeadPipelineRequest(@NotNull UUID pipelineId, @NotNull UUID stageId) {}
