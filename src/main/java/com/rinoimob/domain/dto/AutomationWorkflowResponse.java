package com.rinoimob.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutomationWorkflowResponse {
    private UUID id;
    private String name;
    private String description;
    private Boolean isActive;
    private WorkflowConfigDto workflowConfig;
    private Integer version;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
