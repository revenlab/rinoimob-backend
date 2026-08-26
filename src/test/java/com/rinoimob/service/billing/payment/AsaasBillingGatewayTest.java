package com.rinoimob.service.billing.payment;

import com.rinoimob.service.billing.payment.dto.BillingCustomerRequest;
import com.rinoimob.service.billing.payment.dto.BillingCustomerResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsaasBillingGatewayTest {

    @Test
    void createOrUpdateCustomer_restoresAndUpdatesStoredCustomerWhenItWasDeleted() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AsaasBillingGateway gateway = new AsaasBillingGateway(
                restTemplate, "https://api-sandbox.asaas.com", "token", "/v3/checkouts", "60", "CREDIT_CARD"
        );
        BillingCustomerRequest request = new BillingCustomerRequest(
                "Customer", "customer@example.com", "24971563792", "47999999999", "Rua Teste", "10",
                null, "89223005", "Centro", "tenant-id"
        );
        when(restTemplate.exchange(
                eq("https://api-sandbox.asaas.com/v3/customers/cus_deleted"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of("id", "cus_deleted", "deleted", true)));
        when(restTemplate.exchange(
                eq("https://api-sandbox.asaas.com/v3/customers/cus_deleted/restore"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of("id", "cus_deleted", "deleted", false)));
        when(restTemplate.exchange(
                eq("https://api-sandbox.asaas.com/v3/customers/cus_deleted"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of("id", "cus_deleted", "name", "Customer", "email", "customer@example.com")));

        BillingCustomerResult result = gateway.createOrUpdateCustomer("cus_deleted", request);

        assertThat(result.customerId()).isEqualTo("cus_deleted");
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://api-sandbox.asaas.com/v3/customers/cus_deleted"), eq(HttpMethod.PUT), entityCaptor.capture(), eq(Map.class)
        );
        assertThat(entityCaptor.getValue().getBody()).isEqualTo(Map.of(
                "name", "Customer", "email", "customer@example.com", "cpfCnpj", "24971563792",
                "mobilePhone", "47999999999", "address", "Rua Teste", "addressNumber", "10",
                "postalCode", "89223005", "province", "Centro", "externalReference", "tenant-id"
        ));
    }
}
