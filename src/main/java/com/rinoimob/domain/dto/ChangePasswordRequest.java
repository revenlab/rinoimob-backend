package com.rinoimob.domain.dto;

import com.rinoimob.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
    @NotBlank(message = "Current password is required")
    String currentPassword,

    @NotBlank(message = "New password is required")
    @ValidPassword
    String newPassword,

    @NotBlank(message = "Confirm password is required")
    String confirmPassword
) {}
