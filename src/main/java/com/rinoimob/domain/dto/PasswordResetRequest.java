package com.rinoimob.domain.dto;

import com.rinoimob.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequest(
    @NotBlank(message = "Token is required")
    String token,

    @NotBlank(message = "Password is required")
    @ValidPassword
    String password,

    @NotBlank(message = "Confirm password is required")
    String confirmPassword
) {
}
