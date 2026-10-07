package com.rinoimob.domain.entity;

import com.rinoimob.domain.enums.IntegrationConnectionStatus;
import com.rinoimob.domain.enums.IntegrationProvider;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "integration_connections")
@Data
@NoArgsConstructor
public class IntegrationConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IntegrationProvider provider;
    @Column(nullable = false, length = 160)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IntegrationConnectionStatus status = IntegrationConnectionStatus.CONNECTING;
    @Column(name = "external_account_id", length = 160)
    private String externalAccountId;
    @Column(name = "external_account_name", length = 200)
    private String externalAccountName;
    @Column(name = "encrypted_credentials", columnDefinition = "TEXT")
    private String encryptedCredentials;
    @Column(name = "inbound_key", nullable = false, unique = true, length = 80)
    private String inboundKey;
    @Column(name = "outbound_key", nullable = false, unique = true, length = 80)
    private String outboundKey;
    @Type(JsonBinaryType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> config = new HashMap<>();
    @Column(name = "last_received_at")
    private LocalDateTime lastReceivedAt;
    @Column(name = "last_processed_at")
    private LocalDateTime lastProcessedAt;
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void createTimestamps() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (inboundKey == null) inboundKey = UUID.randomUUID().toString();
        if (outboundKey == null) outboundKey = UUID.randomUUID().toString();
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}
