package com.rinoimob.domain.dto;

import java.util.UUID;

public record InviteUserRequest(
    String email,
    String firstName,
    String lastName,
    String phone,
    UUID roleId
) {}
