package com.rinoimob.domain.entity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name = "rental_documents") @Data @NoArgsConstructor
public class RentalDocument {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "contract_id", nullable = false) private UUID contractId;
    @Column(nullable = false) private String name;
    @Column(name = "file_fid", nullable = false) private String fileFid;
    @Column(name = "file_url", nullable = false) private String fileUrl;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}
