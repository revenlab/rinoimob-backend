package com.rinoimob.domain.entity;

import com.rinoimob.domain.enums.PropertyType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "tenant_property_types",
        uniqueConstraints = @UniqueConstraint(name = "uk_tenant_property_types_tenant_code", columnNames = {"tenant_id", "code"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantPropertyType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PropertyType code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "card_color", length = 20)
    private String cardColor;

    @Column(name = "cover_image_fid", length = 255)
    private String coverImageFid;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
