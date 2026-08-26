package com.rinoimob.service.billing.payment;

import com.rinoimob.service.billing.payment.dto.BillingCheckoutRequest;
import com.rinoimob.service.billing.payment.dto.BillingCheckoutResult;
import com.rinoimob.service.billing.payment.dto.BillingCustomerRequest;
import com.rinoimob.service.billing.payment.dto.BillingCustomerResult;
import com.rinoimob.service.billing.payment.dto.BillingProviderPage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.HttpClientErrorException;
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
            @Value("${billing.asaas.billing-types:CREDIT_CARD}") String billingTypes) {
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
    public BillingCustomerResult createOrUpdateCustomer(String existingCustomerId, BillingCustomerRequest request) {
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(buildCustomerBody(request), buildHeaders());
        String customerId = resolveCustomerId(existingCustomerId, request);
        boolean existingCustomer = customerId != null;
        ResponseEntity<Map> response = restTemplate.exchange(
                existingCustomer ? baseUrl + "/v3/customers/" + customerId : baseUrl + "/v3/customers",
                existingCustomer ? HttpMethod.PUT : HttpMethod.POST,
                entity,
                Map.class
        );
        Map<String, Object> body = requireBody(response.getBody(), "customer");
        return new BillingCustomerResult(
                firstNonBlank(valueAsString(body.get("id")), customerId),
                valueAsString(body.get("email")),
                valueAsString(body.get("name"))
        );
    }

    private String resolveCustomerId(String existingCustomerId, BillingCustomerRequest request) {
        if (existingCustomerId != null && !existingCustomerId.isBlank()) {
            try {
                ResponseEntity<Map> response = restTemplate.exchange(
                        baseUrl + "/v3/customers/" + existingCustomerId,
                        HttpMethod.GET,
                        new HttpEntity<>(buildHeaders()),
                        Map.class
                );
                Map<String, Object> body = requireBody(response.getBody(), "customer");
                if (Boolean.TRUE.equals(body.get("deleted"))) {
                    restoreCustomer(existingCustomerId);
                }
                return existingCustomerId;
            } catch (HttpClientErrorException.NotFound ignored) {
                // The saved identifier may belong to a different environment or have been purged.
            }
        }

        String listUrl = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v3/customers")
                .queryParam("externalReference", request.externalReference())
                .queryParam("cpfCnpj", request.cpfCnpj())
                .queryParam("limit", 100)
                .toUriString();
        ResponseEntity<Map> response = restTemplate.exchange(
                listUrl, HttpMethod.GET, new HttpEntity<>(buildHeaders()), Map.class
        );
        Object data = requireBody(response.getBody(), "customer list").get("data");
        if (!(data instanceof List<?> customers)) {
            return null;
        }
        return customers.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(customer -> !Boolean.TRUE.equals(customer.get("deleted")))
                .map(customer -> valueAsString(customer.get("id")))
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .orElse(null);
    }

    private void restoreCustomer(String customerId) {
        restTemplate.exchange(
                baseUrl + "/v3/customers/" + customerId + "/restore",
                HttpMethod.POST,
                new HttpEntity<>(buildHeaders()),
                Map.class
        );
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

    @Override
    public void inactivateSubscription(String providerSubscriptionId) {
        updateSubscription(providerSubscriptionId, Map.of("status", "INACTIVE"));
    }

    @Override
    public void reactivateSubscription(String providerSubscriptionId, LocalDate nextDueDate) {
        updateSubscription(providerSubscriptionId, Map.of(
                "status", "ACTIVE",
                "nextDueDate", nextDueDate.toString()
        ));
    }

    @Override
    public void updateSubscriptionPlan(String providerSubscriptionId, BigDecimal value, LocalDate nextDueDate) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("value", value);
        body.put("nextDueDate", nextDueDate.toString());
        body.put("updatePendingPayments", false);
        updateSubscription(providerSubscriptionId, body);
    }

    @Override
    public void updateSubscriptionCardToken(String providerSubscriptionId, String creditCardToken, String remoteIp) {
        if (creditCardToken == null || creditCardToken.isBlank()) {
            throw new IllegalArgumentException("creditCardToken is required");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("creditCardToken", creditCardToken);
        body.put("remoteIp", remoteIp);
        restTemplate.exchange(
                baseUrl + "/v3/subscriptions/" + providerSubscriptionId + "/creditCard",
                HttpMethod.PUT,
                new HttpEntity<>(body, buildHeaders()),
                Map.class
        );
    }

    private void updateSubscription(String providerSubscriptionId, Map<String, Object> body) {
        if (providerSubscriptionId == null || providerSubscriptionId.isBlank()) {
            throw new IllegalArgumentException("providerSubscriptionId is required");
        }
        restTemplate.exchange(
                baseUrl + "/v3/subscriptions/" + providerSubscriptionId,
                HttpMethod.PUT,
                new HttpEntity<>(body, buildHeaders()),
                Map.class
        );
    }

    @Override
    public BillingProviderPage listCustomerPayments(String providerCustomerId, int offset, int limit) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v3/payments")
                .queryParam("customer", providerCustomerId)
                .queryParam("offset", offset)
                .queryParam("limit", Math.min(Math.max(limit, 1), 100))
                .toUriString();
        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(buildHeaders()), Map.class
        );
        Map<String, Object> body = requireBody(response.getBody(), "payment list");
        List<Map<String, Object>> data = new java.util.ArrayList<>();
        if (body.get("data") instanceof List<?> values) {
            for (Object value : values) {
                if (value instanceof Map<?, ?> rawMap) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    rawMap.forEach((key, itemValue) -> item.put(String.valueOf(key), itemValue));
                    data.add(item);
                }
            }
        }
        return new BillingProviderPage(data, Boolean.TRUE.equals(body.get("hasMore")));
    }

    @Override
    public Map<String, Object> getSubscription(String providerSubscriptionId) {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/v3/subscriptions/" + providerSubscriptionId,
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders()),
                Map.class
        );
        return requireBody(response.getBody(), "subscription");
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

    private Map<String, Object> buildCustomerBody(BillingCustomerRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", request.name());
        body.put("email", request.email());
        body.put("cpfCnpj", request.cpfCnpj());
        body.put("mobilePhone", request.phone());
        body.put("address", request.address());
        body.put("addressNumber", request.addressNumber());
        body.put("postalCode", request.postalCode());
        body.put("province", request.province());
        body.put("externalReference", request.externalReference());
        if (request.addressComplement() != null && !request.addressComplement().isBlank()) {
            body.put("complement", request.addressComplement());
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
