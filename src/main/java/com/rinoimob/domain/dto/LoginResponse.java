package com.rinoimob.domain.dto;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    Long expiresIn,
    UserDto user
) {
}
