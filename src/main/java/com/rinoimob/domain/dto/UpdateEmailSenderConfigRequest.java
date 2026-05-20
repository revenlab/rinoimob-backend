package com.rinoimob.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UpdateEmailSenderConfigRequest {

    @NotBlank(message = "displayName is required")
    @Size(max = 100)
    private String displayName;

    @NotBlank(message = "fromEmail is required")
    @Email(message = "fromEmail must be a valid email")
    @Size(max = 255)
    private String fromEmail;

    @Size(max = 100)
    private String fromName;

    @NotBlank(message = "smtpHost is required")
    @Size(max = 255)
    private String smtpHost;

    @NotNull(message = "smtpPort is required")
    @Min(value = 1, message = "smtpPort must be between 1 and 65535")
    @Max(value = 65535, message = "smtpPort must be between 1 and 65535")
    private Integer smtpPort;

    @Size(max = 255)
    private String smtpUsername;

    /** Null means "keep existing password unchanged". */
    private String smtpPassword;

    private Boolean smtpTls;

    private Boolean isDefault;
}
