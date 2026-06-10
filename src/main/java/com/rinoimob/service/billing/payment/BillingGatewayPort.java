package com.rinoimob.service.billing.payment;

import com.rinoimob.service.billing.payment.dto.BillingCheckoutRequest;
import com.rinoimob.service.billing.payment.dto.BillingCheckoutResult;
import com.rinoimob.service.billing.payment.dto.BillingCustomerResult;

public interface BillingGatewayPort {

    BillingCustomerResult createCustomer(String email, String name);

    BillingCheckoutResult createCheckout(BillingCheckoutRequest request);

    void cancelSubscription(String providerSubscriptionId);
}
