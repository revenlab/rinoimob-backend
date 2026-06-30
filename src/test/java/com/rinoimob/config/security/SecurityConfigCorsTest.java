package com.rinoimob.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigCorsTest {

    private final SecurityConfig securityConfig = new SecurityConfig(null, null, null, null);

    @Test
    void shouldAllowExternalCustomDomainsOnlyForPublicApiCors() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource(
                "https://app.rinoimob.com,https://www.rinoimob.com",
                "https://*"
        );

        CorsConfiguration publicConfig = source.getCorsConfiguration(request("/api/v1/public/config"));
        CorsConfiguration authenticatedConfig = source.getCorsConfiguration(request("/api/v1/users"));

        assertThat(publicConfig).isNotNull();
        assertThat(publicConfig.checkOrigin("https://cliente.com.br")).isEqualTo("https://cliente.com.br");
        assertThat(publicConfig.getAllowCredentials()).isFalse();

        assertThat(authenticatedConfig).isNotNull();
        assertThat(authenticatedConfig.checkOrigin("https://cliente.com.br")).isNull();
        assertThat(authenticatedConfig.checkOrigin("https://app.rinoimob.com")).isEqualTo("https://app.rinoimob.com");
        assertThat(authenticatedConfig.getAllowCredentials()).isTrue();
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
    }
}
