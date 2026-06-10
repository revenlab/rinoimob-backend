package com.rinoimob.domain.entity;

import com.rinoimob.domain.enums.BillingPlanCode;
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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 30)
    private BillingPlanCode code;

    @Column(name = "plan_name", nullable = false, length = 100)
    private String planName;

    @Column(name = "monthly_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyPrice = BigDecimal.ZERO;

    @Column(name = "annual_price", precision = 12, scale = 2)
    private BigDecimal annualPrice;

    @Column(name = "max_properties")
    private Integer maxProperties;

    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "max_leads_per_month")
    private Integer maxLeadsPerMonth;

    @Column(name = "max_whatsapp_numbers")
    private Integer maxWhatsappNumbers;

    @Column(name = "blog_enabled", nullable = false)
    private Boolean blogEnabled = false;

    @Column(name = "custom_domain_enabled", nullable = false)
    private Boolean customDomainEnabled = false;

    @Column(name = "automation_crm_enabled", nullable = false)
    private Boolean automationCrmEnabled = false;

    @Column(name = "public_api_enabled", nullable = false)
    private Boolean publicApiEnabled = false;

    @Column(name = "vip_support_enabled", nullable = false)
    private Boolean vipSupportEnabled = false;

    @Column(name = "custom_implementation_enabled", nullable = false)
    private Boolean customImplementationEnabled = false;

    @Column(name = "features", columnDefinition = "TEXT")
    private String features;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private Boolean active = true;

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
}
