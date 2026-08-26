package com.rinoimob.domain.entity;

import com.rinoimob.domain.enums.BillingSubscriptionChangeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_subscription_changes")
@Data
@NoArgsConstructor
public class TenantSubscriptionChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_plan_id", nullable = false)
    private BillingPlan sourcePlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_plan_id", nullable = false)
    private BillingPlan targetPlan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BillingSubscriptionChangeStatus status;

    @Column(name = "external_reference", nullable = false, unique = true, length = 200)
    private String externalReference;

    @Column(name = "provider_checkout_id", length = 100)
    private String providerCheckoutId;

    @Column(name = "provider_checkout_url", length = 500)
    private String providerCheckoutUrl;

    @Column(name = "previous_provider_subscription_id", length = 100)
    private String previousProviderSubscriptionId;

    @Column(name = "new_provider_subscription_id", length = 100)
    private String newProviderSubscriptionId;

    @Column(name = "requested_by_user_id")
    private UUID requestedByUserId;

    @Column(name = "effective_at")
    private LocalDateTime effectiveAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
