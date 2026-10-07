package com.rinoimob.domain.entity;

import com.rinoimob.domain.enums.IntegrationEventStatus;
import com.rinoimob.domain.enums.IntegrationProvider;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "integration_events")
@Data
@NoArgsConstructor
public class IntegrationEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IntegrationProvider provider;
    @Column(name = "external_event_id", nullable = false, length = 200)
    private String externalEventId;
    @Column(name = "external_resource_id", length = 160)
    private String externalResourceId;
    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IntegrationEventStatus status = IntegrationEventStatus.RECEIVED;
    @Column(name = "encrypted_payload", nullable = false, columnDefinition = "TEXT")
    private String encryptedPayload;
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt = LocalDateTime.now();
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
    @Column(name = "lead_id")
    private UUID leadId;
    @Column(name = "provider_created_at")
    private LocalDateTime providerCreatedAt;
    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void createTimestamps() {
        receivedAt = LocalDateTime.now();
        updatedAt = receivedAt;
        if (nextAttemptAt == null) nextAttemptAt = receivedAt;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}
