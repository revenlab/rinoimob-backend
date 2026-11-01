package com.rinoimob.domain.entity;

import com.rinoimob.domain.enums.RentalEnums.PartyType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "rental_parties") @Data @NoArgsConstructor
public class RentalParty {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PartyType type;
    @Column(nullable = false, length = 200) private String name;
    @Column(name = "cpf_cnpj", length = 14) private String cpfCnpj;
    private String email;
    private String phone;
    @Column(columnDefinition = "TEXT") private String address;
    @Column(name = "pix_key") private String pixKey;
    @Column(name = "pix_key_type", length = 20) private String pixKeyType;
    @Column(name = "provider_customer_id", length = 100) private String providerCustomerId;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}
