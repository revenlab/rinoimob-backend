package com.rinoimob.domain.dto.integration;

import jakarta.validation.constraints.NotBlank;

public record CreateGrupoOlxConnectionRequest(@NotBlank String name) {}
