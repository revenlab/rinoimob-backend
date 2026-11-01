package com.rinoimob.domain.dto.integration;

import java.util.List;
import java.util.Map;

public record GrupoOlxFeedStatusResponse(
        String feedUrl,
        boolean ready,
        int activeProperties,
        int publishedProperties,
        Map<String, Integer> skippedProperties,
        List<String> warnings
) {}
