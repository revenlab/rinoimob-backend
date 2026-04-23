package com.rinoimob.interceptor;

import com.rinoimob.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantInterceptorTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private final TenantInterceptor interceptor = new TenantInterceptor();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testPreHandleWithTenantIdHeader() throws Exception {
        when(request.getHeader("X-Tenant-ID")).thenReturn("test-tenant-123");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(TenantContext.getTenantId()).isEqualTo("test-tenant-123");
    }

    @Test
    void testPreHandleWithSubdomain() throws Exception {
        when(request.getHeader("X-Tenant-ID")).thenReturn(null);
        when(request.getServerName()).thenReturn("mycompany.example.com");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(TenantContext.getTenantId()).isEqualTo("mycompany");
    }

    @Test
    void testPreHandleWithLocalhost() throws Exception {
        when(request.getHeader("X-Tenant-ID")).thenReturn(null);
        when(request.getServerName()).thenReturn("localhost:8080");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    void testAfterCompletion() throws Exception {
        TenantContext.setTenantId("test-tenant-123");

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void testPreHandleWithoutTenantId() throws Exception {
        when(request.getHeader("X-Tenant-ID")).thenReturn(null);
        when(request.getServerName()).thenReturn("localhost");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

}
