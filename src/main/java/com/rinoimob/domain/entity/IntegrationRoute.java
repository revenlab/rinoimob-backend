package com.rinoimob.domain.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "integration_routes")
@Data
@NoArgsConstructor
public class IntegrationRoute {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;
    @Column(name = "external_resource_id", nullable = false, length = 160)
    private String externalResourceId;
    @Column(name = "external_resource_name", length = 200)
    private String externalResourceName;
    @Column(name = "pipeline_id", nullable = false)
    private UUID pipelineId;
    @Column(name = "pool_id")
    private UUID poolId;
    @Column(name = "assigned_to")
    private UUID assignedTo;
    @Column(name = "default_property_id")
    private UUID defaultPropertyId;
    @Type(JsonBinaryType.class)
    @Column(name = "field_mapping", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> fieldMapping = new HashMap<>();
    @Column(nullable = false)
    private Boolean active = true;
    @Column(name = "meta_sync_from")
    private Instant metaSyncFrom;
    @Column(name = "meta_sync_until")
    private Instant metaSyncUntil;
    @Column(name = "meta_sync_cursor", columnDefinition = "TEXT")
    private String metaSyncCursor;
    @Column(name = "meta_synced_until")
    private Instant metaSyncedUntil;
    @Column(name = "meta_last_sync_at")
    private Instant metaLastSyncAt;
    @Column(name = "meta_next_sync_at", nullable = false)
    private Instant metaNextSyncAt = Instant.EPOCH;
    @Column(name = "meta_sync_failures", nullable = false)
    private int metaSyncFailures;
    @Column(name = "meta_sync_error", length = 500)
    private String metaSyncError;
    @Column(name = "meta_recovered_count", nullable = false)
    private long metaRecoveredCount;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void createTimestamps() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}
