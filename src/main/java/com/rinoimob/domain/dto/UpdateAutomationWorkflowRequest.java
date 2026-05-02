package com.rinoimob.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAutomationWorkflowRequest {
    private String name;
    private String description;
    private WorkflowConfigDto workflowConfig;
    private Boolean isActive;
}
