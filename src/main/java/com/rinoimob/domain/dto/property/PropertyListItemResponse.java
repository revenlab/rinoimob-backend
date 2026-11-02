package com.rinoimob.domain.dto.property;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** Private listing metadata. Never include user-dependent access in public caches. */
public record PropertyListItemResponse(
        @JsonUnwrapped PropertySummaryResponse property,
        boolean canEdit
) {}
