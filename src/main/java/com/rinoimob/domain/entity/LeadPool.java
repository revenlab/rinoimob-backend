package com.rinoimob.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lead_pools")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadPool {

    @Column(columnDefinition = "jsonb")
    private String criteria;

    @Column
    private Integer priority = 100;

    @Column(name = "routing_strategy", length = 32)
    private String routingStrategy = "ROUND_ROBIN";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Backwards-compatible constructors used by tests and other code
    public LeadPool(UUID id, UUID tenantId, String name, String description, LocalDateTime createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }

    public LeadPool(UUID id, UUID tenantId, String name, String description, LocalDateTime createdAt, String criteria, Integer priority, String routingStrategy) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.criteria = criteria;
        this.priority = priority;
        this.routingStrategy = routingStrategy;
    }
}

