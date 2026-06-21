package com.rinoimob.service.onboarding;

import com.rinoimob.domain.dto.UpdateOnboardingProgressRequest;
import com.rinoimob.domain.entity.UserOnboardingProgress;
import com.rinoimob.domain.enums.OnboardingStatus;
import com.rinoimob.domain.repository.UserOnboardingProgressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserOnboardingServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    private UserOnboardingProgressRepository userOnboardingProgressRepository;

    @InjectMocks
    private UserOnboardingService userOnboardingService;

    @Test
    void getSummaryOrDefault_returnsNotStartedWhenThereIsNoProgress() {
        when(userOnboardingProgressRepository.findByTenantIdAndUserIdAndTutorialKey(
                TENANT_ID, USER_ID, UserOnboardingService.APP_CRM_CORE_V1
        )).thenReturn(Optional.empty());

        var response = userOnboardingService.getSummaryOrDefault(
                TENANT_ID,
                USER_ID,
                UserOnboardingService.APP_CRM_CORE_V1
        );

        assertThat(response.tutorialKey()).isEqualTo(UserOnboardingService.APP_CRM_CORE_V1);
        assertThat(response.status()).isEqualTo(OnboardingStatus.NOT_STARTED);
        assertThat(response.lastStepKey()).isNull();
        assertThat(response.lastRoute()).isNull();
    }

    @Test
    void updateProgress_createsNewProgressWhenThereIsNoExistingRow() {
        UpdateOnboardingProgressRequest request = new UpdateOnboardingProgressRequest(
                OnboardingStatus.IN_PROGRESS,
                "dashboard-overview",
                "/dashboard"
        );

        when(userOnboardingProgressRepository.findByTenantIdAndUserIdAndTutorialKey(
                TENANT_ID, USER_ID, UserOnboardingService.APP_CRM_CORE_V1
        )).thenReturn(Optional.empty());
        when(userOnboardingProgressRepository.save(any(UserOnboardingProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = userOnboardingService.updateProgress(
                TENANT_ID,
                USER_ID,
                UserOnboardingService.APP_CRM_CORE_V1,
                request
        );

        ArgumentCaptor<UserOnboardingProgress> captor = ArgumentCaptor.forClass(UserOnboardingProgress.class);
        verify(userOnboardingProgressRepository).save(captor.capture());

        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getStatus()).isEqualTo(OnboardingStatus.IN_PROGRESS);
        assertThat(captor.getValue().getStartedAt()).isNotNull();
        assertThat(response.status()).isEqualTo(OnboardingStatus.IN_PROGRESS);
        assertThat(response.lastStepKey()).isEqualTo("dashboard-overview");
    }

    @Test
    void updateProgress_updatesExistingRowIdempotently() {
        UserOnboardingProgress existing = new UserOnboardingProgress();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(TENANT_ID);
        existing.setUserId(USER_ID);
        existing.setTutorialKey(UserOnboardingService.APP_CRM_CORE_V1);
        existing.setStatus(OnboardingStatus.IN_PROGRESS);
        existing.setStartedAt(LocalDateTime.now().minusMinutes(5));

        UpdateOnboardingProgressRequest request = new UpdateOnboardingProgressRequest(
                OnboardingStatus.COMPLETED,
                "tasks-next-steps",
                "/tarefas"
        );

        when(userOnboardingProgressRepository.findByTenantIdAndUserIdAndTutorialKey(
                TENANT_ID, USER_ID, UserOnboardingService.APP_CRM_CORE_V1
        )).thenReturn(Optional.of(existing));
        when(userOnboardingProgressRepository.save(existing)).thenReturn(existing);

        var response = userOnboardingService.updateProgress(
                TENANT_ID,
                USER_ID,
                UserOnboardingService.APP_CRM_CORE_V1,
                request
        );

        assertThat(existing.getStatus()).isEqualTo(OnboardingStatus.COMPLETED);
        assertThat(existing.getCompletedAt()).isNotNull();
        assertThat(existing.getDismissedAt()).isNull();
        assertThat(existing.getLastStepKey()).isEqualTo("tasks-next-steps");
        assertThat(existing.getLastRoute()).isEqualTo("/tarefas");
        assertThat(response.status()).isEqualTo(OnboardingStatus.COMPLETED);
    }
}
