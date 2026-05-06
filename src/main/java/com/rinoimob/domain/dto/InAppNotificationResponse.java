package com.rinoimob.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InAppNotificationResponse {

    private UUID id;
    private String title;
    private String message;
    private String type;
    private Boolean isRead;
    private LocalDateTime readAt;
    private String metadata;
    private String actionUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
