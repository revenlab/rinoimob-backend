package com.rinoimob.domain.repository;
import com.rinoimob.domain.entity.RentalCharge;
import com.rinoimob.domain.enums.RentalEnums.ChargeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface RentalChargeRepository extends JpaRepository<RentalCharge, UUID> {
    List<RentalCharge> findByTenantIdOrderByDueDateDesc(UUID tenantId);
    Optional<RentalCharge> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<RentalCharge> findByTenantIdAndContractIdAndCompetence(UUID tenantId, UUID contractId, LocalDate competence);
    Optional<RentalCharge> findByProviderPaymentId(String providerPaymentId);
    long countByTenantIdAndStatus(UUID tenantId, ChargeStatus status);
    @Query("select coalesce(sum(c.totalAmount), 0) from RentalCharge c where c.tenantId=:tenantId and c.status=:status and c.receivedAt>=:start")
    java.math.BigDecimal sumReceivedSince(@Param("tenantId") UUID tenantId, @Param("status") ChargeStatus status, @Param("start") LocalDateTime start);
}
