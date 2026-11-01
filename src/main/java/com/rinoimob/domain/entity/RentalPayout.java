package com.rinoimob.domain.entity;

import com.rinoimob.domain.enums.RentalEnums.PayoutStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity @Table(name = "rental_payouts") @Data @NoArgsConstructor
public class RentalPayout {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "contract_id", nullable = false) private UUID contractId;
    @Column(name = "charge_id", nullable = false) private UUID chargeId;
    @Column(name = "owner_party_id", nullable = false) private UUID ownerPartyId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PayoutStatus status = PayoutStatus.DRAFT;
    @Column(name = "gross_owner_amount", nullable = false) private BigDecimal grossOwnerAmount;
    @Column(name = "management_fee_amount", nullable = false) private BigDecimal managementFeeAmount;
    @Column(name = "net_amount", nullable = false) private BigDecimal netAmount;
    @Column(name = "scheduled_date") private LocalDate scheduledDate;
    @Column(name = "provider_transfer_id") private String providerTransferId;
    @Column(name = "approved_by_user_id") private UUID approvedByUserId;
    @Column(name = "approved_at") private LocalDateTime approvedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "error_message", columnDefinition = "TEXT") private String errorMessage;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}
