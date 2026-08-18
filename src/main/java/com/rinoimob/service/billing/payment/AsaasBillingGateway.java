package com.rinoimob.service.billing.payment;

import com.rinoimob.service.billing.payment.dto.BillingCheckoutRequest;
import com.rinoimob.service.billing.payment.dto.BillingCheckoutResult;
import com.rinoimob.service.billing.payment.dto.BillingCustomerResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AsaasBillingGateway implements BillingGatewayPort {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String apiKey;
    private final String checkoutPath;
    private final String checkoutExpirationMinutes;
    private final List<String> billingTypes;

    public AsaasBillingGateway(
            RestTemplate restTemplate,
            @Value("${billing.asaas.base-url:https://api.asaas.com}") String baseUrl,
            @Value("${billing.asaas.api-key:}") String apiKey,
            @Value("${billing.asaas.checkout-path:/v3/checkouts}") String checkoutPath,
            @Value("${billing.asaas.checkout-expiration-minutes:60}") String checkoutExpirationMinutes,
            @Value("${billing.asaas.billing-types:PIX,CREDIT_CARD}") String billingTypes) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.checkoutPath = checkoutPath;
        this.checkoutExpirationMinutes = checkoutExpirationMinutes;
        this.billingTypes = Arrays.stream(billingTypes.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    @Override
    public BillingCustomerResult createCustomer(String email, String name) {
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("name", name, "email", email), buildHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(baseUrl + "/v3/customers", HttpMethod.POST, entity, Map.class);
        Map<String, Object> body = requireBody(response.getBody(), "customer");
        return new BillingCustomerResult(valueAsString(body.get("id")), valueAsString(body.get("email")), valueAsString(body.get("name")));
    }

    @Override
    public BillingCheckoutResult createCheckout(BillingCheckoutRequest request) {
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(buildCheckoutBody(request), buildHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(baseUrl + checkoutPath, HttpMethod.POST, entity, Map.class);
        Map<String, Object> body = requireBody(response.getBody(), "checkout");
        return new BillingCheckoutResult(
                valueAsString(body.get("id")),
                valueAsString(body.get("link")),
                valueAsString(body.get("customer")),
                null,
                null
        );
    }

    @Override
    public void cancelSubscription(String providerSubscriptionId) {
        if (providerSubscriptionId == null || providerSubscriptionId.isBlank()) {
            throw new IllegalArgumentException("providerSubscriptionId is required");
        }
        restTemplate.exchange(baseUrl + "/v3/subscriptions/" + providerSubscriptionId, HttpMethod.DELETE,
                new HttpEntity<>(buildHeaders()), Void.class);
    }

    private HttpHeaders buildHeaders() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("billing.asaas.api-key is required");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("access_token", apiKey);
        return headers;
    }

    private Map<String, Object> buildCheckoutBody(BillingCheckoutRequest request) {
        if (billingTypes.isEmpty()) {
            throw new IllegalStateException("billing.asaas.billing-types is required");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("billingTypes", billingTypes);
        body.put("chargeTypes", List.of("RECURRENT"));
        body.put("minutesToExpire", Integer.parseInt(checkoutExpirationMinutes));
        body.put("externalReference", request.externalId());
        body.put("callback", Map.of(
                "successUrl", request.successUrl(),
                "cancelUrl", request.cancelUrl(),
                "expiredUrl", request.cancelUrl()
        ));
        body.put("items", List.of(Map.of(
                "externalReference", request.planCode().name(),
                "name", "Rinoimob " + request.planCode().name(),
                "description", "Assinatura mensal Rinoimob",
                "quantity", 1,
                "value", BigDecimal.valueOf(request.amountInCents(), 2)
        )));
        body.put("subscription", Map.of(
                "cycle", "MONTHLY",
                "nextDueDate", LocalDate.now().toString()
        ));
        if (request.customerId() != null && !request.customerId().isBlank()) {
            body.put("customer", request.customerId());
        } else {
            body.put("customerData", Map.of("name", request.customerName(), "email", request.customerEmail()));
        }
        return body;
    }

    private Map<String, Object> requireBody(Map responseBody, String resource) {
        if (responseBody == null) {
            throw new IllegalStateException("Asaas " + resource + " response body is empty");
        }
        return responseBody;
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
