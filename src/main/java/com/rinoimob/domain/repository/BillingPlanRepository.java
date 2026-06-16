package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.BillingPlan;
import com.rinoimob.domain.enums.BillingPlanCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingPlanRepository extends JpaRepository<BillingPlan, UUID> {

    List<BillingPlan> findByTenantIdIsNullAndActiveTrueOrderBySortOrderAsc();

    Optional<BillingPlan> findByCodeAndTenantIdIsNull(BillingPlanCode code);
}
