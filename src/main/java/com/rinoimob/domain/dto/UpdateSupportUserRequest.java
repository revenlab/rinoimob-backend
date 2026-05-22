package com.rinoimob.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateSupportUserRequest(
        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        String phone,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email
) {
}
