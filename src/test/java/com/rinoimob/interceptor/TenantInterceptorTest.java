package com.rinoimob.interceptor;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantInterceptorTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private TenantInterceptor interceptor;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testPreHandleWithTenantIdHeader() throws Exception {
        String tenantId = UUID.randomUUID().toString();
        when(request.getHeader("X-Tenant-ID")).thenReturn(tenantId);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(TenantContext.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void testPreHandleWithSubdomain() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);

        when(request.getHeader("X-Tenant-ID")).thenReturn(null);
        when(request.getServerName()).thenReturn("mycompany.example.com");
        when(tenantRepository.findBySubdomain("mycompany")).thenReturn(Optional.of(tenant));

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(TenantContext.getTenantId()).isEqualTo(tenantId.toString());
    }

    @Test
    void testPreHandleWithUnknownSubdomain() throws Exception {
        when(request.getHeader("X-Tenant-ID")).thenReturn(null);
        when(request.getServerName()).thenReturn("unknown.example.com");
        when(tenantRepository.findBySubdomain("unknown")).thenReturn(Optional.empty());

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void testPreHandleWithAppSubdomainIsIgnored() throws Exception {
        when(request.getHeader("X-Tenant-ID")).thenReturn(null);
        when(request.getServerName()).thenReturn("app.example.com");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void testPreHandleWithLocalhost() throws Exception {
        when(request.getHeader("X-Tenant-ID")).thenReturn(null);
        when(request.getServerName()).thenReturn("localhost");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void testAfterCompletion() throws Exception {
        TenantContext.setTenantId("test-tenant-123");

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(TenantContext.getTenantId()).isNull();
    }
}
