package com.rinoimob.domain.entity;

import com.rinoimob.domain.enums.RentalEnums.PartyRole;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "rental_contract_parties") @Data @NoArgsConstructor
public class RentalContractParty {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "contract_id", nullable = false) private UUID contractId;
    @Column(name = "party_id", nullable = false) private UUID partyId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PartyRole role;
    @Column(name = "ownership_percent") private BigDecimal ownershipPercent;
    @Column(name = "billing_responsible", nullable = false) private boolean billingResponsible;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}
