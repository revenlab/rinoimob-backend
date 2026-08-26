package com.rinoimob.service.billing;

import com.rinoimob.domain.dto.billing.TenantBillingLimitsSnapshot;
import com.rinoimob.domain.dto.BillingCustomerDetailsResponse;
import com.rinoimob.domain.dto.UpdateBillingCustomerDetailsRequest;
import com.rinoimob.domain.entity.BillingPlan;
import com.rinoimob.domain.entity.TenantBillingProfile;
import com.rinoimob.domain.repository.TenantBillingProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenantBillingProfileService {

    private final TenantBillingProfileRepository tenantBillingProfileRepository;
    private final BillingPlanResolverService billingPlanResolverService;

    public TenantBillingProfileService(TenantBillingProfileRepository tenantBillingProfileRepository,
                                       BillingPlanResolverService billingPlanResolverService) {
        this.tenantBillingProfileRepository = tenantBillingProfileRepository;
        this.billingPlanResolverService = billingPlanResolverService;
    }

    @Transactional(readOnly = true)
    public TenantBillingLimitsSnapshot resolveEffectiveLimits(UUID tenantId) {
        return tenantBillingProfileRepository.findByTenantId(tenantId)
                .map(this::toSnapshot)
                .orElseGet(() -> toSnapshot(tenantId, billingPlanResolverService.resolveEffectivePlan(tenantId)));
    }

    @Transactional
    public TenantBillingProfile ensureProfileForPlan(UUID tenantId, BillingPlan billingPlan, UUID updatedByUserId, String notes) {
        return tenantBillingProfileRepository.findByTenantId(tenantId)
                .orElseGet(() -> createProfileFromPlan(tenantId, billingPlan, updatedByUserId, notes));
    }

    @Transactional
    public TenantBillingProfile replaceProfileForPlan(UUID tenantId, BillingPlan billingPlan, UUID updatedByUserId, String notes) {
        TenantBillingProfile profile = tenantBillingProfileRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    TenantBillingProfile created = new TenantBillingProfile();
                    created.setTenantId(tenantId);
                    return created;
                });

        profile.setBillingPlan(billingPlan);
        profile.setMaxUsers(TenantBillingLimitsSnapshot.normalizeLimit(billingPlan.getMaxUsers()));
        profile.setMaxProperties(TenantBillingLimitsSnapshot.normalizeLimit(billingPlan.getMaxProperties()));
        profile.setMaxLeadsPerMonth(TenantBillingLimitsSnapshot.normalizeLimit(billingPlan.getMaxLeadsPerMonth()));
        profile.setMaxWhatsappNumbers(TenantBillingLimitsSnapshot.normalizeLimit(billingPlan.getMaxWhatsappNumbers()));
        profile.setBlogEnabled(Boolean.TRUE.equals(billingPlan.getBlogEnabled()));
        profile.setCustomDomainEnabled(Boolean.TRUE.equals(billingPlan.getCustomDomainEnabled()));
        profile.setAutomationCrmEnabled(Boolean.TRUE.equals(billingPlan.getAutomationCrmEnabled()));
        profile.setPublicApiEnabled(Boolean.TRUE.equals(billingPlan.getPublicApiEnabled()));
        profile.setVipSupportEnabled(Boolean.TRUE.equals(billingPlan.getVipSupportEnabled()));
        profile.setCustomImplementationEnabled(Boolean.TRUE.equals(billingPlan.getCustomImplementationEnabled()));
        profile.setUpdatedByUserId(updatedByUserId);
        profile.setNotes(notes);
        return tenantBillingProfileRepository.save(profile);
    }

    @Transactional
    public BillingCustomerDetailsResponse updateCustomerDetails(UUID tenantId, BillingPlan billingPlan, UUID userId,
                                                                 UpdateBillingCustomerDetailsRequest request) {
        TenantBillingProfile profile = ensureProfileForPlan(tenantId, billingPlan, userId, "Billing customer details updated");
        profile.setBillingCpfCnpj(digitsOnly(request.cpfCnpj()));
        profile.setBillingPhone(digitsOnly(request.phone()));
        profile.setBillingAddress(request.address().trim());
        profile.setBillingAddressNumber(request.addressNumber().trim());
        profile.setBillingAddressComplement(blankToNull(request.addressComplement()));
        profile.setBillingPostalCode(digitsOnly(request.postalCode()));
        profile.setBillingProvince(request.province().trim());
        profile.setUpdatedByUserId(userId);
        return toCustomerDetails(tenantBillingProfileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public BillingCustomerDetailsResponse getCustomerDetails(UUID tenantId, BillingPlan billingPlan) {
        return tenantBillingProfileRepository.findByTenantId(tenantId)
                .map(this::toCustomerDetails)
                .orElseGet(() -> new BillingCustomerDetailsResponse(null, null, null, null, null, null, null, false));
    }

    @Transactional(readOnly = true)
    public String getProviderCustomerId(UUID tenantId) {
        return tenantBillingProfileRepository.findByTenantId(tenantId)
                .map(TenantBillingProfile::getProviderCustomerId)
                .orElse(null);
    }

    @Transactional
    public void saveProviderCustomerId(UUID tenantId, BillingPlan billingPlan, String providerCustomerId) {
        if (providerCustomerId == null || providerCustomerId.isBlank()) {
            return;
        }
        TenantBillingProfile profile = ensureProfileForPlan(
                tenantId, billingPlan, null, "Asaas customer identity synchronized"
        );
        profile.setProviderCustomerId(providerCustomerId);
        tenantBillingProfileRepository.save(profile);
    }

    private TenantBillingProfile createProfileFromPlan(UUID tenantId, BillingPlan billingPlan, UUID updatedByUserId, String notes) {
        TenantBillingProfile profile = new TenantBillingProfile();
        profile.setTenantId(tenantId);
        profile.setBillingPlan(billingPlan);
        profile.setMaxUsers(TenantBillingLimitsSnapshot.normalizeLimit(billingPlan.getMaxUsers()));
        profile.setMaxProperties(TenantBillingLimitsSnapshot.normalizeLimit(billingPlan.getMaxProperties()));
        profile.setMaxLeadsPerMonth(TenantBillingLimitsSnapshot.normalizeLimit(billingPlan.getMaxLeadsPerMonth()));
        profile.setMaxWhatsappNumbers(TenantBillingLimitsSnapshot.normalizeLimit(billingPlan.getMaxWhatsappNumbers()));
        profile.setBlogEnabled(Boolean.TRUE.equals(billingPlan.getBlogEnabled()));
        profile.setCustomDomainEnabled(Boolean.TRUE.equals(billingPlan.getCustomDomainEnabled()));
        profile.setAutomationCrmEnabled(Boolean.TRUE.equals(billingPlan.getAutomationCrmEnabled()));
        profile.setPublicApiEnabled(Boolean.TRUE.equals(billingPlan.getPublicApiEnabled()));
        profile.setVipSupportEnabled(Boolean.TRUE.equals(billingPlan.getVipSupportEnabled()));
        profile.setCustomImplementationEnabled(Boolean.TRUE.equals(billingPlan.getCustomImplementationEnabled()));
        profile.setUpdatedByUserId(updatedByUserId);
        profile.setNotes(notes);
        return tenantBillingProfileRepository.save(profile);
    }

    private TenantBillingLimitsSnapshot toSnapshot(TenantBillingProfile profile) {
        BillingPlan plan = profile.getBillingPlan();
        return new TenantBillingLimitsSnapshot(
                profile.getTenantId(),
                plan.getCode(),
                plan.getPlanName(),
                profile.getMaxUsers(),
                profile.getMaxProperties(),
                profile.getMaxLeadsPerMonth(),
                profile.getMaxWhatsappNumbers(),
                Boolean.TRUE.equals(profile.getBlogEnabled()),
                Boolean.TRUE.equals(profile.getCustomDomainEnabled()),
                Boolean.TRUE.equals(profile.getAutomationCrmEnabled()),
                Boolean.TRUE.equals(profile.getPublicApiEnabled()),
                Boolean.TRUE.equals(profile.getVipSupportEnabled()),
                Boolean.TRUE.equals(profile.getCustomImplementationEnabled())
        );
    }

    private BillingCustomerDetailsResponse toCustomerDetails(TenantBillingProfile profile) {
        boolean complete = isPresent(profile.getBillingCpfCnpj())
                && isPresent(profile.getBillingPhone())
                && isPresent(profile.getBillingAddress())
                && isPresent(profile.getBillingAddressNumber())
                && isPresent(profile.getBillingPostalCode())
                && isPresent(profile.getBillingProvince());
        return new BillingCustomerDetailsResponse(
                profile.getBillingCpfCnpj(), profile.getBillingPhone(), profile.getBillingAddress(),
                profile.getBillingAddressNumber(), profile.getBillingAddressComplement(), profile.getBillingPostalCode(),
                profile.getBillingProvince(), complete
        );
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private String digitsOnly(String value) {
        return value == null ? null : value.replaceAll("\\D", "");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private TenantBillingLimitsSnapshot toSnapshot(UUID tenantId, BillingPlan plan) {
        return new TenantBillingLimitsSnapshot(
                tenantId,
                plan.getCode(),
                plan.getPlanName(),
                TenantBillingLimitsSnapshot.normalizeLimit(plan.getMaxUsers()),
                TenantBillingLimitsSnapshot.normalizeLimit(plan.getMaxProperties()),
                TenantBillingLimitsSnapshot.normalizeLimit(plan.getMaxLeadsPerMonth()),
                TenantBillingLimitsSnapshot.normalizeLimit(plan.getMaxWhatsappNumbers()),
                Boolean.TRUE.equals(plan.getBlogEnabled()),
                Boolean.TRUE.equals(plan.getCustomDomainEnabled()),
                Boolean.TRUE.equals(plan.getAutomationCrmEnabled()),
                Boolean.TRUE.equals(plan.getPublicApiEnabled()),
                Boolean.TRUE.equals(plan.getVipSupportEnabled()),
                Boolean.TRUE.equals(plan.getCustomImplementationEnabled())
        );
    }
}
