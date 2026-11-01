package com.rinoimob.domain.entity;

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
@Table(name = "lead_acquisitions")
@Data
@NoArgsConstructor
public class LeadAcquisition {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "lead_id", nullable = false, unique = true)
    private UUID leadId;
    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IntegrationProvider provider;
    @Column(name = "external_lead_id", nullable = false, length = 200)
    private String externalLeadId;
    @Column(name = "external_form_id", length = 160)
    private String externalFormId;
    @Column(name = "external_listing_id", length = 160)
    private String externalListingId;
    @Column(name = "campaign_name")
    private String campaignName;
    @Column(name = "ad_name")
    private String adName;
    @Column(name = "property_match_status", nullable = false, length = 30)
    private String propertyMatchStatus = "NOT_APPLICABLE";
    @Column(name = "provider_created_at")
    private LocalDateTime providerCreatedAt;
    @Type(JsonBinaryType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void createTimestamp() {
        createdAt = LocalDateTime.now();
    }
}
