package com.rinoimob.domain.dto.integration;

import jakarta.validation.constraints.NotBlank;

public record ActivateMetaConnectionRequest(
        @NotBlank String pageId,
        @NotBlank String pageName
) {}
