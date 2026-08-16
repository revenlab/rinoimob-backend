package com.rinoimob.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequestEmailChangeRequest(
        @NotBlank(message = "New email is required")
        @Email(message = "New email must be valid")
        String newEmail,

        @NotBlank(message = "Current password is required")
        String currentPassword
) {
}
