package com.rinoimob.domain.entity;

import com.rinoimob.domain.enums.RentalEnums.*;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity @Table(name = "rental_contracts") @Data @NoArgsConstructor
public class RentalContract {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "property_id", nullable = false) private UUID propertyId;
    @Column(name = "source_lead_id") private UUID sourceLeadId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ContractStatus status = ContractStatus.DRAFT;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Column(name = "due_day", nullable = false) private Integer dueDay;
    @Column(name = "charge_lead_days", nullable = false) private Integer chargeLeadDays = 10;
    @Column(name = "payout_day", nullable = false) private Integer payoutDay;
    @Column(name = "billing_type", nullable = false) private String billingType = "BOLETO";
    @Column(name = "base_rent", nullable = false, precision = 14, scale = 2) private BigDecimal baseRent;
    @Column(name = "condo_amount", nullable = false) private BigDecimal condoAmount = BigDecimal.ZERO;
    @Column(name = "iptu_amount", nullable = false) private BigDecimal iptuAmount = BigDecimal.ZERO;
    @Column(name = "insurance_amount", nullable = false) private BigDecimal insuranceAmount = BigDecimal.ZERO;
    @Column(name = "discount_amount", nullable = false) private BigDecimal discountAmount = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING) @Column(name = "management_fee_type", nullable = false) private ManagementFeeType managementFeeType = ManagementFeeType.PERCENTAGE;
    @Column(name = "management_fee_value", nullable = false) private BigDecimal managementFeeValue = BigDecimal.ZERO;
    @Column(name = "late_fee_percent", nullable = false) private BigDecimal lateFeePercent = BigDecimal.valueOf(2);
    @Column(name = "monthly_interest_percent", nullable = false) private BigDecimal monthlyInterestPercent = BigDecimal.ONE;
    @Enumerated(EnumType.STRING) @Column(name = "guarantee_type", nullable = false) private GuaranteeType guaranteeType = GuaranteeType.NONE;
    @Column(name = "guarantee_details", columnDefinition = "TEXT") private String guaranteeDetails;
    @Enumerated(EnumType.STRING) @Column(name = "adjustment_index", nullable = false) private AdjustmentIndex adjustmentIndex = AdjustmentIndex.MANUAL;
    @Column(name = "next_adjustment_date") private LocalDate nextAdjustmentDate;
    @Column(name = "activated_at") private LocalDateTime activatedAt;
    @Column(name = "ended_at") private LocalDateTime endedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}
