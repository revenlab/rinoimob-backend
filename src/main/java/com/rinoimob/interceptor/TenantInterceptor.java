package com.rinoimob.interceptor;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantInterceptor implements HandlerInterceptor {

    private final TenantRepository tenantRepository;

    public TenantInterceptor(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String tenantId = request.getHeader("X-Tenant-ID");

        if (tenantId != null && !tenantId.isBlank()) {
            TenantContext.setTenantId(tenantId);
            return true;
        }

        String subdomain = extractSubdomainFromHost(request.getServerName());
        if (subdomain != null) {
            tenantRepository.findBySubdomain(subdomain)
                    .ifPresent(tenant -> TenantContext.setTenantId(tenant.getId().toString()));
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }

    private String extractSubdomainFromHost(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            return null;
        }
        String[] parts = serverName.split("\\.");
        if (parts.length > 1 && !parts[0].equals("localhost") && !parts[0].equals("www") && !parts[0].equals("app")) {
            return parts[0];
        }
        return null;
    }

}
