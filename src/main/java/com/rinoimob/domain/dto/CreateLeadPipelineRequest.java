package com.rinoimob.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateLeadPipelineRequest(@NotBlank String name, String description,
                                        @NotEmpty List<OpenStageRequest> stages, List<String> sources) {
    public record OpenStageRequest(@NotBlank String name) {}
}
