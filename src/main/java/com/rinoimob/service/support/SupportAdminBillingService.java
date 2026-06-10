package com.rinoimob.service.support;

import com.rinoimob.domain.dto.SupportBillingPlanOptionResponse;
import com.rinoimob.domain.dto.SupportTenantBillingResponse;
import com.rinoimob.domain.dto.UpdateSupportTenantBillingRequest;
import com.rinoimob.domain.dto.billing.TenantBillingLimitsSnapshot;
import com.rinoimob.domain.entity.BillingPlan;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.entity.TenantBillingProfile;
import com.rinoimob.domain.entity.TenantSubscription;
import com.rinoimob.domain.repository.TenantBillingProfileRepository;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.TenantSubscriptionRepository;
import com.rinoimob.service.billing.BillingPlanResolverService;
import com.rinoimob.service.billing.TenantBillingProfileService;
import com.rinoimob.service.billing.TenantSubscriptionService;
import com.rinoimob.service.core.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupportAdminBillingService {

    private final TenantRepository tenantRepository;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final TenantBillingProfileRepository tenantBillingProfileRepository;
    private final BillingPlanResolverService billingPlanResolverService;
    private final TenantSubscriptionService tenantSubscriptionService;
    private final TenantBillingProfileService tenantBillingProfileService;
    private final AuditService auditService;

    @Transactional
    public SupportTenantBillingResponse getTenantBilling(UUID actorTenantId, UUID actorUserId, UUID tenantId) {
        ensureTenantExists(tenantId);
        TenantSubscription subscription = tenantSubscriptionRepository.findByTenantId(tenantId)
                .orElseGet(() -> tenantSubscriptionService.ensureFreeSubscriptionForTenant(tenantId));
        tenantBillingProfileService.ensureProfileForPlan(tenantId, subscription.getBillingPlan(), actorUserId, "Billing profile initialized");

        TenantBillingLimitsSnapshot snapshot = tenantBillingProfileService.resolveEffectiveLimits(tenantId);
        TenantBillingProfile profile = tenantBillingProfileRepository.findByTenantId(tenantId).orElse(null);
        List<SupportBillingPlanOptionResponse> plans = billingPlanResolverService.listCatalogPlans().stream()
                .map(this::toPlanOption)
                .toList();

        auditService.log(
                tenantId.toString(),
                actorUserId != null ? actorUserId.toString() : null,
                "SUPPORT_VIEW_TENANT_BILLING",
                "TENANT_BILLING",
                tenantId.toString(),
                "actorTenant=" + actorTenantId + ", actorUser=" + actorUserId
        );

        return toResponse(subscription, snapshot, profile, plans);
    }

    @Transactional
    public SupportTenantBillingResponse updateTenantBilling(UUID actorTenantId,
                                                            UUID actorUserId,
                                                            UUID tenantId,
                                                            UpdateSupportTenantBillingRequest request) {
        ensureTenantExists(tenantId);

        TenantSubscription subscription = tenantSubscriptionService.ensureFreeSubscriptionForTenant(tenantId);
        BillingPlan targetPlan = request.planCode() != null
                ? billingPlanResolverService.getRequiredGlobalPlan(request.planCode())
                : subscription.getBillingPlan();

        boolean planChanged = !subscription.getBillingPlan().getId().equals(targetPlan.getId());
        if (planChanged) {
            subscription.setBillingPlan(targetPlan);
            subscription = tenantSubscriptionRepository.save(subscription);
        }

        TenantBillingProfile profile = tenantBillingProfileRepository.findByTenantId(tenantId)
                .orElseGet(() -> tenantBillingProfileService.ensureProfileForPlan(tenantId, targetPlan, actorUserId, "Billing profile initialized by support"));

        if (planChanged) {
            applyPlanDefaults(profile, targetPlan);
        }

        applyOverrides(profile, request);
        profile.setBillingPlan(targetPlan);
        profile.setUpdatedByUserId(actorUserId);
        if (request.notes() != null) {
            profile.setNotes(request.notes().isBlank() ? null : request.notes().trim());
        }
        TenantBillingProfile savedProfile = tenantBillingProfileRepository.save(profile);

        TenantBillingLimitsSnapshot snapshot = tenantBillingProfileService.resolveEffectiveLimits(tenantId);
        List<SupportBillingPlanOptionResponse> plans = billingPlanResolverService.listCatalogPlans().stream()
                .map(this::toPlanOption)
                .toList();

        auditService.log(
                tenantId.toString(),
                actorUserId != null ? actorUserId.toString() : null,
                "TENANT_BILLING_UPDATED",
                "TENANT_BILLING",
                tenantId.toString(),
                "actorTenant=" + actorTenantId
                        + ", actorUser=" + actorUserId
                        + ", planCode=" + targetPlan.getCode()
        );

        return toResponse(subscription, snapshot, savedProfile, plans);
    }

    private SupportTenantBillingResponse toResponse(TenantSubscription subscription,
                                                    TenantBillingLimitsSnapshot snapshot,
                                                    TenantBillingProfile profile,
                                                    List<SupportBillingPlanOptionResponse> plans) {
        return new SupportTenantBillingResponse(
                subscription.getTenantId(),
                snapshot.planCode(),
                snapshot.planName(),
                subscription.getStatus(),
                subscription.getProvider(),
                subscription.getProviderCustomerId(),
                subscription.getProviderSubscriptionId(),
                subscription.getProviderCheckoutId(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()),
                snapshot.maxUsers(),
                snapshot.maxProperties(),
                snapshot.maxLeadsPerMonth(),
                snapshot.maxWhatsappNumbers(),
                snapshot.blogEnabled(),
                snapshot.customDomainEnabled(),
                snapshot.automationCrmEnabled(),
                snapshot.publicApiEnabled(),
                snapshot.vipSupportEnabled(),
                snapshot.customImplementationEnabled(),
                profile != null ? profile.getNotes() : null,
                profile != null ? profile.getUpdatedByUserId() : null,
                profile != null ? profile.getUpdatedAt() : null,
                plans
        );
    }

    private void applyPlanDefaults(TenantBillingProfile profile, BillingPlan plan) {
        profile.setMaxUsers(TenantBillingLimitsSnapshot.normalizeLimit(plan.getMaxUsers()));
        profile.setMaxProperties(TenantBillingLimitsSnapshot.normalizeLimit(plan.getMaxProperties()));
        profile.setMaxLeadsPerMonth(TenantBillingLimitsSnapshot.normalizeLimit(plan.getMaxLeadsPerMonth()));
        profile.setMaxWhatsappNumbers(TenantBillingLimitsSnapshot.normalizeLimit(plan.getMaxWhatsappNumbers()));
        profile.setBlogEnabled(Boolean.TRUE.equals(plan.getBlogEnabled()));
        profile.setCustomDomainEnabled(Boolean.TRUE.equals(plan.getCustomDomainEnabled()));
        profile.setAutomationCrmEnabled(Boolean.TRUE.equals(plan.getAutomationCrmEnabled()));
        profile.setPublicApiEnabled(Boolean.TRUE.equals(plan.getPublicApiEnabled()));
        profile.setVipSupportEnabled(Boolean.TRUE.equals(plan.getVipSupportEnabled()));
        profile.setCustomImplementationEnabled(Boolean.TRUE.equals(plan.getCustomImplementationEnabled()));
    }

    private void applyOverrides(TenantBillingProfile profile, UpdateSupportTenantBillingRequest request) {
        if (request.maxUsers() != null) {
            profile.setMaxUsers(validateLimit("maxUsers", request.maxUsers()));
        }
        if (request.maxProperties() != null) {
            profile.setMaxProperties(validateLimit("maxProperties", request.maxProperties()));
        }
        if (request.maxLeadsPerMonth() != null) {
            profile.setMaxLeadsPerMonth(validateLimit("maxLeadsPerMonth", request.maxLeadsPerMonth()));
        }
        if (request.maxWhatsappNumbers() != null) {
            profile.setMaxWhatsappNumbers(validateLimit("maxWhatsappNumbers", request.maxWhatsappNumbers()));
        }
        if (request.blogEnabled() != null) {
            profile.setBlogEnabled(request.blogEnabled());
        }
        if (request.customDomainEnabled() != null) {
            profile.setCustomDomainEnabled(request.customDomainEnabled());
        }
        if (request.automationCrmEnabled() != null) {
            profile.setAutomationCrmEnabled(request.automationCrmEnabled());
        }
        if (request.publicApiEnabled() != null) {
            profile.setPublicApiEnabled(request.publicApiEnabled());
        }
        if (request.vipSupportEnabled() != null) {
            profile.setVipSupportEnabled(request.vipSupportEnabled());
        }
        if (request.customImplementationEnabled() != null) {
            profile.setCustomImplementationEnabled(request.customImplementationEnabled());
        }
    }

    private int validateLimit(String field, int value) {
        if (value < TenantBillingLimitsSnapshot.UNLIMITED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be >= -1");
        }
        return value;
    }

    private SupportBillingPlanOptionResponse toPlanOption(BillingPlan plan) {
        return new SupportBillingPlanOptionResponse(
                plan.getCode(),
                plan.getPlanName(),
                plan.getMonthlyPrice(),
                plan.getAnnualPrice(),
                plan.getMaxUsers(),
                plan.getMaxProperties(),
                plan.getMaxLeadsPerMonth(),
                plan.getMaxWhatsappNumbers(),
                Boolean.TRUE.equals(plan.getBlogEnabled()),
                Boolean.TRUE.equals(plan.getCustomDomainEnabled()),
                Boolean.TRUE.equals(plan.getAutomationCrmEnabled()),
                Boolean.TRUE.equals(plan.getPublicApiEnabled()),
                Boolean.TRUE.equals(plan.getVipSupportEnabled()),
                Boolean.TRUE.equals(plan.getCustomImplementationEnabled())
        );
    }

    private Tenant ensureTenantExists(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }
}
