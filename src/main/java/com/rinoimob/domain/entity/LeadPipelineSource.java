package com.rinoimob.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lead_pipeline_sources")
@Data
public class LeadPipelineSource {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "pipeline_id", nullable = false) private UUID pipelineId;
    @Column(nullable = false) private String source;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
