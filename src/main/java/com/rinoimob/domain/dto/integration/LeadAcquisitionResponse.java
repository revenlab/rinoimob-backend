package com.rinoimob.domain.dto.integration;

import com.rinoimob.domain.enums.IntegrationProvider;

import java.time.LocalDateTime;
public record LeadAcquisitionResponse(
        IntegrationProvider provider,
        String externalLeadId,
        String externalFormId,
        String externalListingId,
        String campaignName,
        String adName,
        String propertyMatchStatus,
        LocalDateTime providerCreatedAt
) {}
