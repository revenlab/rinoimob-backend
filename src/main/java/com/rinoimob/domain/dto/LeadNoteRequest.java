package com.rinoimob.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record LeadNoteRequest(
        @NotBlank String content
) {}
