package com.rinoimob.service.billing.payment;

import com.rinoimob.service.billing.payment.dto.BillingCheckoutRequest;
import com.rinoimob.service.billing.payment.dto.BillingCheckoutResult;
import com.rinoimob.service.billing.payment.dto.BillingCustomerResult;
import com.rinoimob.domain.enums.BillingPlanCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class AbacatePayBillingGateway implements BillingGatewayPort {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String apiKey;
    private final String checkoutPath;
    private final String cancelPath;
    private final String defaultMethod;
    private final String starterProductId;
    private final String primeProductId;
    private final String ultimateProductId;

    public AbacatePayBillingGateway(
            RestTemplate restTemplate,
            @Value("${billing.abacatepay.base-url:https://api.abacatepay.com}") String baseUrl,
            @Value("${billing.abacatepay.api-key:}") String apiKey,
            @Value("${billing.abacatepay.checkout-path:/v2/subscriptions/create}") String checkoutPath,
            @Value("${billing.abacatepay.cancel-path:/v2/subscriptions/cancel}") String cancelPath,
            @Value("${billing.abacatepay.default-method:CARD}") String defaultMethod,
            @Value("${billing.abacatepay.plan-products.starter-id:}") String starterProductId,
            @Value("${billing.abacatepay.plan-products.prime-id:}") String primeProductId,
            @Value("${billing.abacatepay.plan-products.ultimate-id:}") String ultimateProductId) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.checkoutPath = checkoutPath;
        this.cancelPath = cancelPath;
        this.defaultMethod = defaultMethod;
        this.starterProductId = starterProductId;
        this.primeProductId = primeProductId;
        this.ultimateProductId = ultimateProductId;
    }

    @Override
    public BillingCustomerResult createCustomer(String email, String name) {
        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(Map.of(
                "email", email,
                "name", name
        ), buildHeaders());

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/v2/customers/create",
                HttpMethod.POST,
                httpEntity,
                Map.class
        );
        return mapCustomerResult(response.getBody());
    }

    @Override
    public BillingCheckoutResult createCheckout(BillingCheckoutRequest request) {
        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(buildCheckoutBody(request), buildHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + checkoutPath,
                HttpMethod.POST,
                httpEntity,
                Map.class
        );
        return mapCheckoutResult(response.getBody());
    }

    @Override
    public void cancelSubscription(String providerSubscriptionId) {
        if (providerSubscriptionId == null || providerSubscriptionId.isBlank()) {
            throw new IllegalArgumentException("providerSubscriptionId is required");
        }
        HttpEntity<Map<String, String>> httpEntity = new HttpEntity<>(Map.of("id", providerSubscriptionId), buildHeaders());
        restTemplate.exchange(
                baseUrl + cancelPath,
                HttpMethod.POST,
                httpEntity,
                Void.class
        );
    }

    private HttpHeaders buildHeaders() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("billing.abacatepay.api-key is required");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }

    private Map<String, Object> buildCheckoutBody(BillingCheckoutRequest request) {
        String productId = resolvePlanProductId(request.planCode());
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("items", List.of(Map.of("id", productId, "quantity", 1)));
        String externalId = request.externalId();
        if (externalId == null || externalId.isBlank()) {
            externalId = request.tenantId().toString();
        }
        body.put("externalId", externalId);
        body.put("completionUrl", request.successUrl());
        body.put("returnUrl", request.cancelUrl());
        body.put("methods", List.of(defaultMethod));
        if (request.customerId() != null && !request.customerId().isBlank()) {
            body.put("customerId", request.customerId());
        }
        body.put("metadata", Map.of(
                "tenantId", request.tenantId().toString(),
                "planCode", request.planCode().name(),
                "customerEmail", request.customerEmail(),
                "customerName", request.customerName()
        ));
        return body;
    }

    @SuppressWarnings("unchecked")
    private BillingCheckoutResult mapCheckoutResult(Map responseBody) {
        if (responseBody == null) {
            throw new IllegalStateException("AbacatePay response body is empty");
        }

        Map<String, Object> data = (Map<String, Object>) responseBody.getOrDefault("data", responseBody);
        String providerCustomerId = valueAsString(data.get("customerId"));
        if (providerCustomerId == null) {
            Map<String, Object> customer = (Map<String, Object>) data.get("customer");
            if (customer != null) {
                providerCustomerId = valueAsString(customer.get("id"));
            }
        }
        String providerSubscriptionId = firstNonBlank(
                valueAsString(data.get("subscriptionId")),
                valueAsString(data.get("subscription_id"))
        );
        String expiresAt = firstNonBlank(
                valueAsString(data.get("expiresAt")),
                valueAsString(data.get("expires_at"))
        );

        return new BillingCheckoutResult(
                valueAsString(data.get("id")),
                valueAsString(data.get("url")),
                providerCustomerId,
                providerSubscriptionId,
                expiresAt
        );
    }

    @SuppressWarnings("unchecked")
    private BillingCustomerResult mapCustomerResult(Map responseBody) {
        if (responseBody == null) {
            throw new IllegalStateException("AbacatePay customer response body is empty");
        }

        Map<String, Object> data = (Map<String, Object>) responseBody.getOrDefault("data", responseBody);
        return new BillingCustomerResult(
                valueAsString(data.get("id")),
                valueAsString(data.get("email")),
                valueAsString(data.get("name"))
        );
    }

    private String resolvePlanProductId(BillingPlanCode planCode) {
        return switch (planCode) {
            case STARTER -> requireProductId(starterProductId, planCode);
            case PRIME -> requireProductId(primeProductId, planCode);
            case ULTIMATE -> requireProductId(ultimateProductId, planCode);
            case FREE -> throw new IllegalStateException("FREE plan does not require AbacatePay checkout");
        };
    }

    private String requireProductId(String productId, BillingPlanCode planCode) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalStateException("billing.abacatepay.plan-products." + planCode.name().toLowerCase() + "-id is required");
        }
        return productId;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
