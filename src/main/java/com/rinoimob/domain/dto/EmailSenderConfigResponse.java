package com.rinoimob.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailSenderConfigResponse {
    private UUID id;
    private String displayName;
    private String fromEmail;
    private String fromName;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    /** Password is never returned via the API. */
    private Boolean smtpTls;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
