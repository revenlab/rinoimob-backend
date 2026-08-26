package com.rinoimob.service.billing.payment;

import com.rinoimob.service.billing.payment.dto.BillingCheckoutRequest;
import com.rinoimob.service.billing.payment.dto.BillingCheckoutResult;
import com.rinoimob.service.billing.payment.dto.BillingCustomerRequest;
import com.rinoimob.service.billing.payment.dto.BillingCustomerResult;
import com.rinoimob.service.billing.payment.dto.BillingProviderPage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public interface BillingGatewayPort {

    BillingCustomerResult createOrUpdateCustomer(String existingCustomerId, BillingCustomerRequest request);

    BillingCheckoutResult createCheckout(BillingCheckoutRequest request);

    void cancelSubscription(String providerSubscriptionId);

    void inactivateSubscription(String providerSubscriptionId);

    void reactivateSubscription(String providerSubscriptionId, LocalDate nextDueDate);

    void updateSubscriptionPlan(String providerSubscriptionId, BigDecimal value, LocalDate nextDueDate);

    void updateSubscriptionCardToken(String providerSubscriptionId, String creditCardToken, String remoteIp);

    BillingProviderPage listCustomerPayments(String providerCustomerId, int offset, int limit);

    Map<String, Object> getSubscription(String providerSubscriptionId);
}
