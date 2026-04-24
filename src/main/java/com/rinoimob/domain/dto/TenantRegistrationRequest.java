package com.rinoimob.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TenantRegistrationRequest(

    @NotBlank(message = "Tenant name is required")
    @Size(min = 2, max = 255, message = "Tenant name must be between 2 and 255 characters")
    String tenantName,

    @NotBlank(message = "Subdomain is required")
    @Size(min = 2, max = 63, message = "Subdomain must be between 2 and 63 characters")
    @Pattern(regexp = "^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?$",
             message = "Subdomain must contain only alphanumeric characters and hyphens, and cannot start or end with a hyphen")
    String subdomain,

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
    @Size(min = 8, max = 255, message = "Password must be between 8 and 255 characters")
    String password,

    @NotBlank(message = "Confirm password is required")
    String confirmPassword
) {
}
