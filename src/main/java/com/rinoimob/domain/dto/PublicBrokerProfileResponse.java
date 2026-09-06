package com.rinoimob.domain.dto;

public record PublicBrokerProfileResponse(
        String slug,
        String name,
        String phone,
        String bio,
        String photoUrl,
        String instagramUrl,
        String creci
) {}
