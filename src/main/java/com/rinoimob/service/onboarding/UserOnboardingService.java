package com.rinoimob.service.onboarding;

import com.rinoimob.domain.dto.OnboardingSummaryResponse;
import com.rinoimob.domain.dto.UpdateOnboardingProgressRequest;
import com.rinoimob.domain.entity.UserOnboardingProgress;
import com.rinoimob.domain.enums.OnboardingStatus;
import com.rinoimob.domain.repository.UserOnboardingProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserOnboardingService {

    public static final String APP_CRM_CORE_V1 = "APP_CRM_CORE_V1";

    private final UserOnboardingProgressRepository userOnboardingProgressRepository;

    @Transactional(readOnly = true)
    public OnboardingSummaryResponse getSummaryOrDefault(UUID tenantId, UUID userId, String tutorialKey) {
        return userOnboardingProgressRepository.findByTenantIdAndUserIdAndTutorialKey(tenantId, userId, tutorialKey)
                .map(this::toResponse)
                .orElseGet(() -> defaultResponse(tutorialKey));
    }

    @Transactional
    public OnboardingSummaryResponse updateProgress(UUID tenantId,
                                                    UUID userId,
                                                    String tutorialKey,
                                                    UpdateOnboardingProgressRequest request) {
        UserOnboardingProgress progress = userOnboardingProgressRepository
                .findByTenantIdAndUserIdAndTutorialKey(tenantId, userId, tutorialKey)
                .orElseGet(() -> {
                    UserOnboardingProgress created = new UserOnboardingProgress();
                    created.setTenantId(tenantId);
                    created.setUserId(userId);
                    created.setTutorialKey(tutorialKey);
                    return created;
                });

        LocalDateTime now = LocalDateTime.now();
        progress.setStatus(request.status());
        progress.setLastStepKey(request.lastStepKey());
        progress.setLastRoute(request.lastRoute());

        if (request.status() == OnboardingStatus.IN_PROGRESS && progress.getStartedAt() == null) {
            progress.setStartedAt(now);
        }

        if (request.status() == OnboardingStatus.DISMISSED) {
            if (progress.getStartedAt() == null) {
                progress.setStartedAt(now);
            }
            progress.setDismissedAt(now);
            progress.setCompletedAt(null);
        }

        if (request.status() == OnboardingStatus.COMPLETED) {
            if (progress.getStartedAt() == null) {
                progress.setStartedAt(now);
            }
            progress.setCompletedAt(now);
            progress.setDismissedAt(null);
        }

        if (request.status() == OnboardingStatus.NOT_STARTED) {
            progress.setStartedAt(null);
            progress.setDismissedAt(null);
            progress.setCompletedAt(null);
        }

        UserOnboardingProgress saved = userOnboardingProgressRepository.save(progress);
        return toResponse(saved);
    }

    private OnboardingSummaryResponse defaultResponse(String tutorialKey) {
        return new OnboardingSummaryResponse(
                tutorialKey,
                OnboardingStatus.NOT_STARTED,
                null,
                null,
                null,
                null
        );
    }

    private OnboardingSummaryResponse toResponse(UserOnboardingProgress progress) {
        return new OnboardingSummaryResponse(
                progress.getTutorialKey(),
                progress.getStatus(),
                progress.getLastStepKey(),
                progress.getLastRoute(),
                progress.getCompletedAt(),
                progress.getDismissedAt()
        );
    }
}
