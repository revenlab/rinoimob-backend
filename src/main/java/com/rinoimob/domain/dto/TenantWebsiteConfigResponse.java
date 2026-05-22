package com.rinoimob.domain.dto;

public record TenantWebsiteConfigResponse(
        String companyName,
        String logoUrl,
        String faviconUrl,
        String primaryColor,
        String secondaryColor,
        String description,
        String heroTitle,
        String heroSubtitle,
        String phone,
        String email,
        String address,
        String instagramUrl,
        String whatsappNumber,
        String facebookUrl
) {
}
