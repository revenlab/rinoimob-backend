package com.rinoimob.domain.dto.property;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

public record CreateYoutubeVideoRequest(
        @NotBlank
        @Size(max = 500)
        String url,

        @Size(max = 120)
        String title
) implements Serializable {}
