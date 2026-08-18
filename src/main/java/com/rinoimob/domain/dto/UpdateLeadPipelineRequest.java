package com.rinoimob.domain.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record UpdateLeadPipelineRequest(String name, String description,
                                        List<OpenStageRequest> stages, List<String> sources) {
    public record OpenStageRequest(@NotBlank String name) {}
}
