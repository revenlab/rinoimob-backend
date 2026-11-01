package com.rinoimob.domain.entity;

import com.rinoimob.domain.enums.VerificationStatus;
import com.rinoimob.domain.enums.SystemRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "email"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String email;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "public_slug", length = 120)
    private String publicSlug;

    @Column(name = "public_bio", columnDefinition = "TEXT")
    private String publicBio;

    @Column(name = "public_photo_fid")
    private String publicPhotoFid;

    @Column(name = "public_photo_url", columnDefinition = "TEXT")
    private String publicPhotoUrl;

    @Column(name = "public_instagram_url")
    private String publicInstagramUrl;

    @Column(name = "creci", length = 80)
    private String creci;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "system_role")
    @Enumerated(EnumType.STRING)
    private SystemRole systemRole;

    @Column(name = "tenant_role_id")
    private UUID tenantRoleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status")
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "min_valid_token_issued_at")
    private Long minValidTokenIssuedAt;

    @Column(name = "force_password_reset", nullable = false)
    private Boolean forcePasswordReset = false;

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

    public boolean isSystemUser() {
        return systemRole != null;
    }
}
