package com.rinoimob.service.website;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudflareCustomHostnameServiceTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final CloudflareCustomHostnameService service = new CloudflareCustomHostnameService(
            restTemplate,
            "token",
            "zone-id",
            "tenant-origin.example.com"
    );

    @Test
    void shouldCreateCustomHostnameWithoutCustomMetadata() {
        when(restTemplate.exchange(
                eq("https://api.cloudflare.com/client/v4/zones/{zoneId}/custom_hostnames"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(Class.class),
                eq("zone-id")
        )).thenThrow(new IllegalStateException("stop after request capture"));

        assertThatThrownBy(() -> service.createOrUpdate("cliente.example.com"))
                .isInstanceOf(IllegalStateException.class);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String, Object>>> requestCaptor =
                (ArgumentCaptor<HttpEntity<Map<String, Object>>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://api.cloudflare.com/client/v4/zones/{zoneId}/custom_hostnames"),
                eq(HttpMethod.POST),
                requestCaptor.capture(),
                any(Class.class),
                eq("zone-id")
        );

        Map<String, Object> body = requestCaptor.getValue().getBody();
        assertThat(body)
                .containsEntry("hostname", "cliente.example.com")
                .containsKey("ssl")
                .doesNotContainKey("custom_metadata");
    }
}
