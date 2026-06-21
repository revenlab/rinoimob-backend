package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.UserOnboardingProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserOnboardingProgressRepository extends JpaRepository<UserOnboardingProgress, UUID> {

    Optional<UserOnboardingProgress> findByTenantIdAndUserIdAndTutorialKey(UUID tenantId, UUID userId, String tutorialKey);
}
