package com.rinoimob.domain.dto;

import com.rinoimob.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 100)
    String firstName,

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 100)
    String lastName,

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email,

    @NotBlank(message = "Password is required")
    @ValidPassword
    String password,

    @NotBlank(message = "Confirm password is required")
    String confirmPassword
) {
}
