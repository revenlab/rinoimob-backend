package com.rinoimob.domain.entity;

import com.rinoimob.domain.enums.RentalEnums.PayoutTreatment;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "rental_charge_items") @Data @NoArgsConstructor
public class RentalChargeItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "charge_id", nullable = false) private UUID chargeId;
    @Column(nullable = false) private String type;
    @Column(nullable = false) private String description;
    @Column(nullable = false) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(name = "payout_treatment", nullable = false) private PayoutTreatment payoutTreatment;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}
