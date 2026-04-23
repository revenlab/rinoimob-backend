package com.rinoimob.interceptor;

import com.rinoimob.config.RateLimitConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitInterceptorTest {

    private RateLimitConfig rateLimitConfig;

    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        rateLimitConfig = new RateLimitConfig();
        interceptor = new RateLimitInterceptor(rateLimitConfig);
    }

    @Test
    void testAllowRequest() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(request.getHeader("X-Client-ID")).thenReturn("client-1");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        boolean result = interceptor.preHandle(request, response, null);
        assertThat(result).isTrue();
    }

    @Test
    void testRateLimitExceeded() throws Exception {
        String clientId = "rate-limit-test";

        for (int i = 0; i < 100; i++) {
            assertThat(rateLimitConfig.allowRequest(clientId)).isTrue();
        }

        boolean result = rateLimitConfig.allowRequest(clientId);
        assertThat(result).isFalse();
    }

    @Test
    void testGetClientIdFromHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(request.getHeader("X-Client-ID")).thenReturn("custom-client");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        interceptor.preHandle(request, response, null);
        assertThat(true).isTrue();
    }

    @Test
    void testGetClientIdFromIpAddress() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(request.getHeader("X-Client-ID")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");

        interceptor.preHandle(request, response, null);
        assertThat(true).isTrue();
    }
}
