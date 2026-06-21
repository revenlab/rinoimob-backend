package com.rinoimob.domain.entity;

import com.rinoimob.domain.enums.OnboardingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_onboarding_progress", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "user_id", "tutorial_key"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserOnboardingProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tutorial_key", nullable = false, length = 100)
    private String tutorialKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OnboardingStatus status = OnboardingStatus.NOT_STARTED;

    @Column(name = "last_step_key", length = 120)
    private String lastStepKey;

    @Column(name = "last_route", length = 255)
    private String lastRoute;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "dismissed_at")
    private LocalDateTime dismissedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
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
