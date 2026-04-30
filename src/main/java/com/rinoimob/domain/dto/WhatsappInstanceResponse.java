package com.rinoimob.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WhatsappInstanceResponse {
    private UUID id;
    private String instanceName;
    private String displayName;
    private String phoneNumber;
    private String status;
    private LocalDateTime createdAt;
}
