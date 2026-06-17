package com.rinoimob.domain.entity;

import com.rinoimob.domain.enums.PropertyVideoSource;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "property_videos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PropertyVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PropertyVideoSource source;

    @Column(name = "seaweed_fid", length = 100)
    private String seaweedFid;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "youtube_video_id", length = 32)
    private String youtubeVideoId;

    @Column(length = 120)
    private String title;

    @Column(nullable = false)
    private Integer position = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
