package com.rinoimob.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WhatsappMessageResponse {
    private UUID id;
    private String direction; // INBOUND | OUTBOUND
    private String content;
    private String status;
    private UUID instanceId;
    private String instanceDisplayName;
    private UUID sentByUserId;
    private String sentByUserName; // first + last
    private LocalDateTime createdAt;
}
