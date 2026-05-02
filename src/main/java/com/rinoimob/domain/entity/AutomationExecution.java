package com.rinoimob.domain.entity;

import com.rinoimob.domain.enums.WorkflowExecutionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonType;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "automation_executions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutomationExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column
    private String triggerEvent;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private String triggerData;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private String executionPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkflowExecutionStatus status;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private String resultData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
