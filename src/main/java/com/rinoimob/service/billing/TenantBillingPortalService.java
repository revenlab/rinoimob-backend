package com.rinoimob.service.billing;

import com.rinoimob.domain.dto.BillingCustomerDetailsResponse;
import com.rinoimob.domain.dto.BillingInvoiceResponse;
import com.rinoimob.domain.dto.BillingStatusResponse;
import com.rinoimob.domain.dto.PendingBillingChangeResponse;
import com.rinoimob.domain.dto.StartBillingCheckoutRequest;
import com.rinoimob.domain.dto.StartBillingCheckoutResponse;
import com.rinoimob.domain.dto.TenantBillingPlanOptionResponse;
import com.rinoimob.domain.dto.TenantBillingPortalResponse;
import com.rinoimob.domain.dto.UpdateBillingCustomerDetailsRequest;
import com.rinoimob.domain.dto.billing.TenantBillingLimitsSnapshot;
import com.rinoimob.domain.entity.BillingPlan;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.entity.TenantSubscription;
import com.rinoimob.domain.entity.TenantSubscriptionChange;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.enums.BillingPaymentStatus;
import com.rinoimob.domain.enums.BillingPlanCode;
import com.rinoimob.domain.enums.BillingProvider;
import com.rinoimob.domain.enums.BillingProviderOperationType;
import com.rinoimob.domain.enums.BillingSubscriptionChangeStatus;
import com.rinoimob.domain.enums.BillingSubscriptionStatus;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.TenantSubscriptionChangeRepository;
import com.rinoimob.domain.repository.TenantSubscriptionRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.billing.payment.BillingGatewayPort;
import com.rinoimob.service.billing.payment.dto.BillingCheckoutRequest;
import com.rinoimob.service.billing.payment.dto.BillingCheckoutResult;
import com.rinoimob.service.billing.payment.dto.BillingCustomerRequest;
import com.rinoimob.service.billing.payment.dto.BillingCustomerResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
public class TenantBillingPortalService {

    private static final List<BillingSubscriptionChangeStatus> OPEN_CHANGE_STATUSES = List.of(
            BillingSubscriptionChangeStatus.PENDING,
            BillingSubscriptionChangeStatus.PAID,
            BillingSubscriptionChangeStatus.SCHEDULED
    );

    private final TenantSubscriptionService tenantSubscriptionService;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final TenantSubscriptionChangeRepository changeRepository;
    private final TenantBillingProfileService profileService;
    private final BillingPlanResolverService planResolverService;
    private final TenantBillingPaymentService paymentService;
    private final BillingProviderOperationService operationService;
    private final BillingGatewayPort billingGatewayPort;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final String checkoutCallbackBaseUrl;
    private final boolean cardTokenUpdateEnabled;

    public TenantBillingPortalService(TenantSubscriptionService tenantSubscriptionService,
                                      TenantSubscriptionRepository subscriptionRepository,
                                      TenantSubscriptionChangeRepository changeRepository,
                                      TenantBillingProfileService profileService,
                                      BillingPlanResolverService planResolverService,
                                      TenantBillingPaymentService paymentService,
                                      BillingProviderOperationService operationService,
                                      BillingGatewayPort billingGatewayPort,
                                      UserRepository userRepository,
                                      TenantRepository tenantRepository,
                                      @Value("${billing.asaas.checkout-callback-base-url:}") String checkoutCallbackBaseUrl,
                                      @Value("${billing.asaas.card-token-update-enabled:false}") boolean cardTokenUpdateEnabled) {
        this.tenantSubscriptionService = tenantSubscriptionService;
        this.subscriptionRepository = subscriptionRepository;
        this.changeRepository = changeRepository;
        this.profileService = profileService;
        this.planResolverService = planResolverService;
        this.paymentService = paymentService;
        this.operationService = operationService;
        this.billingGatewayPort = billingGatewayPort;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.checkoutCallbackBaseUrl = checkoutCallbackBaseUrl;
        this.cardTokenUpdateEnabled = cardTokenUpdateEnabled;
    }

    @Transactional
    public TenantBillingPortalResponse getCurrentBilling(UUID tenantId) {
        TenantSubscription subscription = tenantSubscriptionService.ensureFreeSubscriptionForTenant(tenantId);
        profileService.ensureProfileForPlan(tenantId, subscription.getBillingPlan(), null, "Billing profile initialized");
        TenantBillingLimitsSnapshot limits = profileService.resolveEffectiveLimits(tenantId);
        TenantSubscriptionChange pendingChange = findOpenChange(tenantId);
        BillingPlan contractedPlan = subscription.getBillingPlan();

        return new TenantBillingPortalResponse(
                tenantId,
                contractedPlan.getCode(),
                contractedPlan.getPlanName(),
                subscription.getStatus(),
                subscription.getProvider(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getPaymentDueDate(),
                subscription.getStatus() == BillingSubscriptionStatus.PAST_DUE
                        || subscription.getStatus() == BillingSubscriptionStatus.SUSPENDED
                        ? subscription.getProviderInvoiceUrl() : null,
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
                profileService.getCustomerDetails(tenantId, contractedPlan),
                planResolverService.listCatalogPlans().stream().map(this::toOption).toList(),
                Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()),
                subscription.getAccessRestrictedAt(),
                toPendingChange(pendingChange),
                paymentService.findNextCharge(tenantId),
                cardTokenUpdateEnabled && subscription.getProviderSubscriptionId() != null
        );
    }

    @Transactional
    public BillingStatusResponse getBillingStatus(UUID tenantId) {
        TenantSubscription subscription = tenantSubscriptionService.ensureFreeSubscriptionForTenant(tenantId);
        TenantBillingLimitsSnapshot limits = profileService.resolveEffectiveLimits(tenantId);
        return new BillingStatusResponse(
                subscription.getBillingPlan().getCode(),
                subscription.getStatus(),
                subscription.getPaymentDueDate(),
                subscription.getAccessRestrictedAt(),
                Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()),
                limits.blogEnabled(),
                limits.customDomainEnabled(),
                limits.automationCrmEnabled(),
                limits.publicApiEnabled(),
                limits.vipSupportEnabled(),
                limits.customImplementationEnabled()
        );
    }

    @Transactional(readOnly = true)
    public Page<BillingInvoiceResponse> listInvoices(UUID tenantId, Collection<BillingPaymentStatus> statuses,
                                                      int page, int size) {
        return paymentService.list(tenantId, statuses, page, size);
    }

    @Transactional
    public BillingCustomerDetailsResponse updateBillingCustomer(UUID tenantId, UUID userId,
                                                                UpdateBillingCustomerDetailsRequest request) {
        TenantSubscription subscription = tenantSubscriptionService.ensureFreeSubscriptionForTenant(tenantId);
        return profileService.updateCustomerDetails(tenantId, subscription.getBillingPlan(), userId, request);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public StartBillingCheckoutResponse startCheckout(UUID tenantId, UUID userId, StartBillingCheckoutRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        TenantSubscription existing = tenantSubscriptionService.ensureFreeSubscriptionForTenant(tenantId);
        TenantSubscription subscription = subscriptionRepository.findByTenantIdForUpdate(tenantId).orElse(existing);
        BillingPlan targetPlan = planResolverService.getRequiredGlobalPlan(request.planCode());

        if (subscription.getBillingPlan().getCode() == targetPlan.getCode()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Selected plan is already active");
        }
        if (findOpenChange(tenantId) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A billing plan change is already pending");
        }

        String externalReference = generateCheckoutExternalId(tenantId, request.planCode());
        if (isDowngrade(subscription.getBillingPlan(), targetPlan)) {
            LocalDateTime effectiveAt = firstNonNull(subscription.getCurrentPeriodEnd(), LocalDateTime.now().plusMonths(1));
            TenantSubscriptionChange change = newChange(
                    tenantId, userId, subscription, targetPlan, externalReference,
                    BillingSubscriptionChangeStatus.SCHEDULED
            );
            change.setEffectiveAt(effectiveAt);
            changeRepository.save(change);
            return new StartBillingCheckoutResponse(
                    null, null, null, change.getStatus().name(), effectiveAt, false
            );
        }

        long amountInCents = toAmountInCents(targetPlan.getMonthlyPrice());
        if (amountInCents <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Selected plan is missing monthly price configuration");
        }
        String callbackBaseUrl = resolveCheckoutCallbackBaseUrl();
        BillingCustomerDetailsResponse customerDetails = profileService.getCustomerDetails(tenantId, subscription.getBillingPlan());
        if (!customerDetails.complete()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Complete billing customer details before starting checkout");
        }

        String customerName = buildCustomerName(user, tenant);
        BillingCheckoutResult checkout;
        try {
            String storedCustomerId = firstNonBlank(
                    profileService.getProviderCustomerId(tenantId), subscription.getProviderCustomerId()
            );
            BillingCustomerResult customer = billingGatewayPort.createOrUpdateCustomer(
                    storedCustomerId,
                    new BillingCustomerRequest(
                            customerName, user.getEmail(), customerDetails.cpfCnpj(), customerDetails.phone(),
                            customerDetails.address(), customerDetails.addressNumber(), customerDetails.addressComplement(),
                            customerDetails.postalCode(), customerDetails.province(), tenantId.toString()
                    )
            );
            profileService.saveProviderCustomerId(tenantId, subscription.getBillingPlan(), customer.customerId());
            subscription.setProviderCustomerId(customer.customerId());
            subscriptionRepository.save(subscription);
            checkout = billingGatewayPort.createCheckout(new BillingCheckoutRequest(
                    tenantId, externalReference, request.planCode(), amountInCents, customerName, user.getEmail(),
                    customer.customerId(), callbackBaseUrl + "/meu-plano?billing=success",
                    callbackBaseUrl + "/meu-plano?billing=cancel"
            ));
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Billing provider is not configured", exception);
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Billing provider request failed with status " + exception.getRawStatusCode(), exception);
        }

        TenantSubscriptionChange change = newChange(
                tenantId, userId, subscription, targetPlan, externalReference,
                BillingSubscriptionChangeStatus.PENDING
        );
        change.setProviderCheckoutId(checkout.checkoutId());
        change.setProviderCheckoutUrl(checkout.checkoutUrl());
        change.setExpiresAt(parseProviderDateTime(checkout.expiresAt()));
        changeRepository.save(change);
        return new StartBillingCheckoutResponse(
                checkout.checkoutId(), checkout.checkoutUrl(), checkout.expiresAt(),
                change.getStatus().name(), null, true
        );
    }

    @Transactional
    public TenantBillingPortalResponse scheduleCancellation(UUID tenantId) {
        TenantSubscription subscription = subscriptionRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
        if (subscription.getProvider() != BillingProvider.ASAAS || subscription.getProviderSubscriptionId() == null
                || subscription.getStatus() == BillingSubscriptionStatus.CANCELED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is no active Asaas subscription to cancel");
        }
        if (!Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd())) {
            subscription.setCancelAtPeriodEnd(true);
            subscription.setCurrentPeriodEnd(firstNonNull(subscription.getCurrentPeriodEnd(), LocalDateTime.now().plusMonths(1)));
            subscriptionRepository.save(subscription);
            operationService.enqueue(
                    tenantId, BillingProviderOperationType.INACTIVATE_SUBSCRIPTION,
                    subscription.getProviderSubscriptionId(), "subscription:" + subscription.getId()
                            + ":cancel-at-period-end:" + UUID.randomUUID(),
                    java.util.Map.of()
            );
        }
        return getCurrentBilling(tenantId);
    }

    @Transactional
    public TenantBillingPortalResponse reactivateCancellation(UUID tenantId) {
        TenantSubscription subscription = subscriptionRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
        if (!Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()) || subscription.getProviderSubscriptionId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Subscription has no scheduled cancellation");
        }
        subscription.setCancelAtPeriodEnd(false);
        subscriptionRepository.save(subscription);
        LocalDate nextDueDate = firstNonNull(subscription.getCurrentPeriodEnd(), LocalDateTime.now().plusDays(1)).toLocalDate();
        operationService.enqueue(
                tenantId, BillingProviderOperationType.REACTIVATE_SUBSCRIPTION,
                subscription.getProviderSubscriptionId(), "subscription:" + subscription.getId()
                        + ":reactivate-cancellation:" + UUID.randomUUID(),
                java.util.Map.of("nextDueDate", nextDueDate.toString())
        );
        return getCurrentBilling(tenantId);
    }

    @Transactional(readOnly = true)
    public void updateCardToken(UUID tenantId, String token, String remoteIp) {
        if (!cardTokenUpdateEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Asaas card token update is not enabled");
        }
        TenantSubscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
        if (subscription.getProviderSubscriptionId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Subscription has no Asaas recurring contract");
        }
        try {
            billingGatewayPort.updateSubscriptionCardToken(subscription.getProviderSubscriptionId(), token, remoteIp);
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Billing provider request failed with status " + exception.getRawStatusCode(), exception);
        }
    }

    private TenantSubscriptionChange newChange(UUID tenantId, UUID userId, TenantSubscription subscription,
                                                BillingPlan targetPlan, String externalReference,
                                                BillingSubscriptionChangeStatus status) {
        TenantSubscriptionChange change = new TenantSubscriptionChange();
        change.setTenantId(tenantId);
        change.setSourcePlan(subscription.getBillingPlan());
        change.setTargetPlan(targetPlan);
        change.setStatus(status);
        change.setExternalReference(externalReference);
        change.setPreviousProviderSubscriptionId(subscription.getProviderSubscriptionId());
        change.setRequestedByUserId(userId);
        return change;
    }

    private TenantSubscriptionChange findOpenChange(UUID tenantId) {
        return changeRepository.findFirstByTenantIdAndStatusInOrderByCreatedAtDesc(tenantId, OPEN_CHANGE_STATUSES)
                .orElse(null);
    }

    private PendingBillingChangeResponse toPendingChange(TenantSubscriptionChange change) {
        if (change == null) {
            return null;
        }
        return new PendingBillingChangeResponse(
                change.getTargetPlan().getCode(), change.getTargetPlan().getPlanName(), change.getStatus(),
                change.getProviderCheckoutUrl(), change.getEffectiveAt(), change.getExpiresAt()
        );
    }

    private TenantBillingPlanOptionResponse toOption(BillingPlan plan) {
        return new TenantBillingPlanOptionResponse(
                plan.getCode(), plan.getPlanName(), plan.getMonthlyPrice(), plan.getAnnualPrice(), plan.getMaxUsers(),
                plan.getMaxProperties(), plan.getMaxLeadsPerMonth(), plan.getMaxWhatsappNumbers(),
                Boolean.TRUE.equals(plan.getBlogEnabled()), Boolean.TRUE.equals(plan.getCustomDomainEnabled()),
                Boolean.TRUE.equals(plan.getAutomationCrmEnabled()), Boolean.TRUE.equals(plan.getPublicApiEnabled()),
                Boolean.TRUE.equals(plan.getVipSupportEnabled()), Boolean.TRUE.equals(plan.getCustomImplementationEnabled())
        );
    }

    private boolean isDowngrade(BillingPlan current, BillingPlan target) {
        return planRank(target) < planRank(current);
    }

    private int planRank(BillingPlan plan) {
        return plan.getSortOrder() == null ? plan.getCode().ordinal() : plan.getSortOrder();
    }

    private String generateCheckoutExternalId(UUID tenantId, BillingPlanCode planCode) {
        return tenantId + "-" + planCode + "-" + UUID.randomUUID();
    }

    private String resolveCheckoutCallbackBaseUrl() {
        if (checkoutCallbackBaseUrl == null || checkoutCallbackBaseUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Asaas checkout callback URL is not configured");
        }
        URI callbackUri;
        try {
            callbackUri = URI.create(checkoutCallbackBaseUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Asaas checkout callback URL is invalid", exception);
        }
        if (!"https".equalsIgnoreCase(callbackUri.getScheme()) || callbackUri.getHost() == null
                || callbackUri.getQuery() != null || callbackUri.getFragment() != null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Asaas checkout callback URL must be an HTTPS base URL");
        }
        return checkoutCallbackBaseUrl.trim().replaceFirst("/+$", "");
    }

    private long toAmountInCents(BigDecimal price) {
        return price == null ? 0 : price.movePointRight(2).longValue();
    }

    private String buildCustomerName(User user, Tenant tenant) {
        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return fullName.isBlank() ? tenant.getName() : fullName;
    }

    private LocalDateTime parseProviderDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
