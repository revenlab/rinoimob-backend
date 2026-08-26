package com.rinoimob.domain.entity;

import com.rinoimob.domain.enums.BillingPaymentStatus;
import com.rinoimob.domain.enums.BillingProvider;
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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_billing_payments")
@Data
@NoArgsConstructor
public class TenantBillingPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BillingProvider provider;

    @Column(name = "provider_payment_id", nullable = false, unique = true, length = 100)
    private String providerPaymentId;

    @Column(name = "provider_subscription_id", length = 100)
    private String providerSubscriptionId;

    @Column(name = "provider_checkout_id", length = 100)
    private String providerCheckoutId;

    @Column(name = "provider_customer_id", length = 100)
    private String providerCustomerId;

    @Column(name = "external_reference", length = 200)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BillingPaymentStatus status;

    @Column(name = "billing_type", length = 30)
    private String billingType;

    @Column(name = "amount", precision = 14, scale = 2)
    private BigDecimal value;

    @Column(name = "net_value", precision = 14, scale = 2)
    private BigDecimal netValue;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "invoice_url", length = 500)
    private String invoiceUrl;

    @Column(name = "receipt_url", length = 500)
    private String receiptUrl;

    @Column(length = 500)
    private String description;

    @Column(name = "last_provider_event_at")
    private LocalDateTime lastProviderEventAt;

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
