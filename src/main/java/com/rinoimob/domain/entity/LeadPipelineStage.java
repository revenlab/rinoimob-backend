package com.rinoimob.domain.entity;

import com.rinoimob.domain.enums.LeadPipelineStageKind;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lead_pipeline_stages")
@Data
public class LeadPipelineStage {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "pipeline_id", nullable = false) private UUID pipelineId;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private int position;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private LeadPipelineStageKind kind;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
