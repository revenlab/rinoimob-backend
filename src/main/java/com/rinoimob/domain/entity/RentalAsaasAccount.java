package com.rinoimob.domain.entity;

import com.rinoimob.domain.enums.RentalEnums.AccountStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "rental_asaas_accounts") @Data @NoArgsConstructor
public class RentalAsaasAccount {
    @Id @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "provider_account_id") private String providerAccountId;
    @Column(name = "wallet_id") private String walletId;
    @Column(name = "encrypted_api_key", columnDefinition = "TEXT") private String encryptedApiKey;
    @Column(name = "webhook_token_hash", length = 64) private String webhookTokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AccountStatus status = AccountStatus.NOT_CONFIGURED;
    @Column(name = "rino_fee_percent", nullable = false) private BigDecimal rinoFeePercent = BigDecimal.ONE;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}
