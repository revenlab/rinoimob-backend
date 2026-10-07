package com.rinoimob.domain.entity;

import com.rinoimob.domain.enums.RentalEnums.ChargeStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity @Table(name = "rental_charges") @Data @NoArgsConstructor
public class RentalCharge {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "contract_id", nullable = false) private UUID contractId;
    @Column(nullable = false) private LocalDate competence;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ChargeStatus status = ChargeStatus.DRAFT;
    @Column(name = "total_amount", nullable = false) private BigDecimal totalAmount;
    @Column(name = "base_rent_amount", nullable = false) private BigDecimal baseRentAmount;
    @Column(name = "rino_fee_percent", nullable = false) private BigDecimal rinoFeePercent;
    @Column(name = "rino_fee_amount", nullable = false) private BigDecimal rinoFeeAmount;
    @Column(name = "provider_payment_id") private String providerPaymentId;
    @Column(name = "provider_customer_id") private String providerCustomerId;
    @Column(name = "invoice_url") private String invoiceUrl;
    @Column(name = "receipt_url") private String receiptUrl;
    @Column(name = "billing_type") private String billingType;
    @Column(name = "received_at") private LocalDateTime receivedAt;
    @Column(name = "last_provider_event_at") private LocalDateTime lastProviderEventAt;
    @Column(name = "error_message", columnDefinition = "TEXT") private String errorMessage;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}
