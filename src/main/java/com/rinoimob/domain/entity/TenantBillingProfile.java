package com.rinoimob.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_billing_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantBillingProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_plan_id", nullable = false)
    private BillingPlan billingPlan;

    @Column(name = "max_users", nullable = false)
    private Integer maxUsers;

    @Column(name = "max_properties", nullable = false)
    private Integer maxProperties;

    @Column(name = "max_leads_per_month", nullable = false)
    private Integer maxLeadsPerMonth;

    @Column(name = "max_whatsapp_numbers", nullable = false)
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

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "billing_cpf_cnpj", length = 14)
    private String billingCpfCnpj;

    @Column(name = "billing_phone", length = 20)
    private String billingPhone;

    @Column(name = "billing_address")
    private String billingAddress;

    @Column(name = "billing_address_number", length = 30)
    private String billingAddressNumber;

    @Column(name = "billing_address_complement")
    private String billingAddressComplement;

    @Column(name = "billing_postal_code", length = 8)
    private String billingPostalCode;

    @Column(name = "billing_province", length = 120)
    private String billingProvince;

    @Column(name = "provider_customer_id", length = 100, unique = true)
    private String providerCustomerId;

    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

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
