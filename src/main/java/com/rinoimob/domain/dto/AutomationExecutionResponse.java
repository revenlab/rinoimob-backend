package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.WorkflowExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutomationExecutionResponse {
    private UUID id;
    private UUID workflowId;
    private String triggerEvent;
    private List<String> executionPath;
    private WorkflowExecutionStatus status;
    private String errorMessage;
    private Map<String, Object> resultData;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
