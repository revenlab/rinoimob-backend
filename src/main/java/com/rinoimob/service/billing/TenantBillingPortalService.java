package com.rinoimob.service.billing;

import com.rinoimob.domain.dto.StartBillingCheckoutRequest;
import com.rinoimob.domain.dto.StartBillingCheckoutResponse;
import com.rinoimob.domain.dto.TenantBillingPlanOptionResponse;
import com.rinoimob.domain.dto.TenantBillingPortalResponse;
import com.rinoimob.domain.dto.billing.TenantBillingLimitsSnapshot;
import com.rinoimob.domain.entity.BillingPlan;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.entity.TenantSubscription;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.enums.BillingPlanCode;
import com.rinoimob.domain.enums.BillingProvider;
import com.rinoimob.domain.enums.BillingSubscriptionStatus;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.TenantSubscriptionRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.billing.payment.BillingGatewayPort;
import com.rinoimob.service.billing.payment.dto.BillingCheckoutRequest;
import com.rinoimob.service.billing.payment.dto.BillingCheckoutResult;
import com.rinoimob.service.billing.payment.dto.BillingCustomerResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class TenantBillingPortalService {

    private final TenantSubscriptionService tenantSubscriptionService;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final TenantBillingProfileService tenantBillingProfileService;
    private final BillingPlanResolverService billingPlanResolverService;
    private final BillingGatewayPort billingGatewayPort;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    public TenantBillingPortalService(TenantSubscriptionService tenantSubscriptionService,
                                      TenantSubscriptionRepository tenantSubscriptionRepository,
                                      TenantBillingProfileService tenantBillingProfileService,
                                      BillingPlanResolverService billingPlanResolverService,
                                      BillingGatewayPort billingGatewayPort,
                                      UserRepository userRepository,
                                      TenantRepository tenantRepository) {
        this.tenantSubscriptionService = tenantSubscriptionService;
        this.tenantSubscriptionRepository = tenantSubscriptionRepository;
        this.tenantBillingProfileService = tenantBillingProfileService;
        this.billingPlanResolverService = billingPlanResolverService;
        this.billingGatewayPort = billingGatewayPort;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public TenantBillingPortalResponse getCurrentBilling(UUID tenantId) {
        TenantSubscription subscription = tenantSubscriptionService.ensureFreeSubscriptionForTenant(tenantId);
        tenantBillingProfileService.ensureProfileForPlan(tenantId, subscription.getBillingPlan(), null, "Billing profile initialized");
        TenantBillingLimitsSnapshot limits = tenantBillingProfileService.resolveEffectiveLimits(tenantId);

        return new TenantBillingPortalResponse(
                tenantId,
                limits.planCode(),
                limits.planName(),
                subscription.getStatus(),
                subscription.getProvider(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                limits.maxUsers(),
                limits.maxProperties(),
                limits.maxLeadsPerMonth(),
                limits.maxWhatsappNumbers(),
                limits.blogEnabled(),
                limits.customDomainEnabled(),
                limits.automationCrmEnabled(),
                limits.publicApiEnabled(),
                limits.vipSupportEnabled(),
                limits.customImplementationEnabled(),
                billingPlanResolverService.listCatalogPlans().stream().map(this::toOption).toList()
        );
    }

    @Transactional
    public StartBillingCheckoutResponse startCheckout(UUID tenantId, UUID userId, StartBillingCheckoutRequest request) {
        if (request.planCode() == BillingPlanCode.FREE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FREE plan does not require checkout");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        BillingPlan targetPlan = billingPlanResolverService.getRequiredGlobalPlan(request.planCode());
        long amountInCents = toAmountInCents(targetPlan.getMonthlyPrice());
        if (amountInCents <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Selected plan is missing monthly price configuration");
        }

        String customerName = buildCustomerName(user, tenant);
        String successUrl = request.successUrl() != null && !request.successUrl().isBlank()
                ? request.successUrl()
                : "http://localhost:5173/meu-plano?billing=success";
        String cancelUrl = request.cancelUrl() != null && !request.cancelUrl().isBlank()
                ? request.cancelUrl()
                : "http://localhost:5173/meu-plano?billing=cancel";

        TenantSubscription subscription = tenantSubscriptionService.ensureFreeSubscriptionForTenant(tenantId);
        String customerId = subscription.getProviderCustomerId();
        if (customerId == null || customerId.isBlank()) {
            BillingCustomerResult customer = billingGatewayPort.createCustomer(user.getEmail(), customerName);
            customerId = customer.customerId();
            subscription.setProviderCustomerId(customerId);
            tenantSubscriptionRepository.save(subscription);
        }

        BillingCheckoutResult checkout;
        try {
            checkout = billingGatewayPort.createCheckout(
                    new BillingCheckoutRequest(
                            tenantId,
                            request.planCode(),
                            amountInCents,
                            customerName,
                            user.getEmail(),
                            customerId,
                            successUrl,
                            cancelUrl
                    )
            );
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Billing provider is not configured", e);
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Billing provider request failed with status " + e.getRawStatusCode(), e);
        }

        subscription.setProvider(BillingProvider.ABACATEPAY);
        subscription.setStatus(BillingSubscriptionStatus.PENDING);
        subscription.setProviderCheckoutId(checkout.checkoutId());
        if (checkout.providerCustomerId() != null && !checkout.providerCustomerId().isBlank()) {
            subscription.setProviderCustomerId(checkout.providerCustomerId());
        }
        subscription.setProviderSubscriptionId(null);
        tenantSubscriptionRepository.save(subscription);

        return new StartBillingCheckoutResponse(
                checkout.checkoutId(),
                checkout.checkoutUrl(),
                checkout.expiresAt()
        );
    }

    private TenantBillingPlanOptionResponse toOption(BillingPlan plan) {
        return new TenantBillingPlanOptionResponse(
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

    private long toAmountInCents(BigDecimal price) {
        if (price == null) {
            return 0L;
        }
        return price.movePointRight(2).longValue();
    }

    private String buildCustomerName(User user, Tenant tenant) {
        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " " + (user.getLastName() == null ? "" : user.getLastName())).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }
        return tenant.getName();
    }
}
