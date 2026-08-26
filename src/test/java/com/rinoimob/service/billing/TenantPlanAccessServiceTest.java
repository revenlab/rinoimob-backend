package com.rinoimob.service.billing;

import com.rinoimob.domain.dto.billing.TenantBillingLimitsSnapshot;
import com.rinoimob.domain.enums.BillingFeature;
import com.rinoimob.domain.enums.BillingPlanCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantPlanAccessServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    private TenantBillingProfileService tenantBillingProfileService;

    private TenantPlanAccessService service;

    @BeforeEach
    void setUp() {
        service = new TenantPlanAccessService(tenantBillingProfileService);
    }

    @Test
    void shouldResolveFeatureFlagsFromEffectiveTenantProfile() {
        when(tenantBillingProfileService.resolveEffectiveLimits(TENANT_ID)).thenReturn(snapshot(
                BillingPlanCode.STARTER, true, true, false, false
        ));

        assertThat(service.isEnabled(TENANT_ID, BillingFeature.BLOG)).isTrue();
        assertThat(service.isEnabled(TENANT_ID, BillingFeature.CUSTOM_DOMAIN)).isTrue();
        assertThat(service.isEnabled(TENANT_ID, BillingFeature.AUTOMATION_CRM)).isFalse();
        assertThat(service.isEnabled(TENANT_ID, BillingFeature.PUBLIC_API)).isFalse();
    }

    @Test
    void shouldReturnPaymentRequiredWithRecommendedPlanWhenFeatureIsDisabled() {
        when(tenantBillingProfileService.resolveEffectiveLimits(TENANT_ID)).thenReturn(snapshot(
                BillingPlanCode.FREE, false, false, false, false
        ));

        assertThatThrownBy(() -> service.requireEnabled(TENANT_ID, BillingFeature.CUSTOM_DOMAIN))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
                    assertThat(exception.getReason()).contains("Domínio customizado", "Starter");
                });
    }

    private TenantBillingLimitsSnapshot snapshot(BillingPlanCode planCode, boolean blogEnabled,
                                                   boolean customDomainEnabled, boolean automationCrmEnabled,
                                                   boolean publicApiEnabled) {
        return new TenantBillingLimitsSnapshot(
                TENANT_ID,
                planCode,
                planCode.name(),
                1,
                10,
                20,
                1,
                blogEnabled,
                customDomainEnabled,
                automationCrmEnabled,
                publicApiEnabled,
                false,
                false
        );
    }
}
