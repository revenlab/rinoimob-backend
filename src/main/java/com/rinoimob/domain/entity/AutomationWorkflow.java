package com.rinoimob.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonType;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "automation_workflows")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutomationWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String workflowConfig;

    @Column
    private Integer version = 1;

    @Column(name = "created_by_id")
    private UUID createdById;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
