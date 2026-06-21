package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.OnboardingSummaryResponse;
import com.rinoimob.domain.dto.UpdateOnboardingProgressRequest;
import com.rinoimob.domain.enums.OnboardingStatus;
import com.rinoimob.service.onboarding.UserOnboardingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserOnboardingControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    private UserOnboardingService userOnboardingService;

    @InjectMocks
    private UserOnboardingController userOnboardingController;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void updateProgress_usesAuthenticatedUserFromTenantContext() {
        TenantContext.setTenantId(TENANT_ID.toString());
        TenantContext.setUserId(USER_ID);

        UpdateOnboardingProgressRequest request = new UpdateOnboardingProgressRequest(
                OnboardingStatus.IN_PROGRESS,
                "dashboard-overview",
                "/dashboard"
        );
        OnboardingSummaryResponse response = new OnboardingSummaryResponse(
                UserOnboardingService.APP_CRM_CORE_V1,
                OnboardingStatus.IN_PROGRESS,
                "dashboard-overview",
                "/dashboard",
                null,
                null
        );

        when(userOnboardingService.updateProgress(TENANT_ID, USER_ID, UserOnboardingService.APP_CRM_CORE_V1, request))
                .thenReturn(response);

        var result = userOnboardingController.updateProgress(UserOnboardingService.APP_CRM_CORE_V1, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(response);
        verify(userOnboardingService).updateProgress(TENANT_ID, USER_ID, UserOnboardingService.APP_CRM_CORE_V1, request);
    }

    @Test
    void updateProgress_throwsUnauthorizedWhenContextIsMissing() {
        UpdateOnboardingProgressRequest request = new UpdateOnboardingProgressRequest(
                OnboardingStatus.DISMISSED,
                "leads-board",
                "/leads"
        );

        assertThatThrownBy(() -> userOnboardingController.updateProgress(UserOnboardingService.APP_CRM_CORE_V1, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
