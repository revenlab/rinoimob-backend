package com.rinoimob.domain.entity;

import com.rinoimob.domain.enums.LeadPoolBrokerSelectionMode;
import com.rinoimob.domain.enums.LeadPoolRoutingStrategy;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.Type;

import io.hypersistence.utils.hibernate.type.json.JsonType;

@Entity
@Table(name = "lead_pools")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadPool {

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private String criteria;

    @Column
    private Integer priority = 100;

    @Enumerated(EnumType.STRING)
    @Column(name = "routing_strategy", length = 32, nullable = false)
    private LeadPoolRoutingStrategy routingStrategy = LeadPoolRoutingStrategy.ROUND_ROBIN;

    @Enumerated(EnumType.STRING)
    @Column(name = "broker_selection_mode", length = 32, nullable = false)
    private LeadPoolBrokerSelectionMode brokerSelectionMode = LeadPoolBrokerSelectionMode.ALL_BROKERS;

    @Column(name = "trigger_after_inactive_days")
    private Integer triggerAfterInactiveDays;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "lead_pool_brokers",
            joinColumns = @JoinColumn(name = "lead_pool_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> brokers = new HashSet<>();

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
        this.routingStrategy = routingStrategy == null ? LeadPoolRoutingStrategy.ROUND_ROBIN : LeadPoolRoutingStrategy.valueOf(routingStrategy);
    }

    public LeadPool(UUID id, UUID tenantId, String name, String description, LocalDateTime createdAt, String criteria, Integer priority, String routingStrategy,
                    LeadPoolBrokerSelectionMode brokerSelectionMode, Integer triggerAfterInactiveDays) {
        this(id, tenantId, name, description, createdAt, criteria, priority, routingStrategy);
        this.brokerSelectionMode = brokerSelectionMode == null ? LeadPoolBrokerSelectionMode.ALL_BROKERS : brokerSelectionMode;
        this.triggerAfterInactiveDays = triggerAfterInactiveDays;
    }
}
