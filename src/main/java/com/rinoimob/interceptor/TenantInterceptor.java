package com.rinoimob.interceptor;

import com.rinoimob.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String tenantId = request.getHeader("X-Tenant-ID");

        if (tenantId == null) {
            tenantId = extractTenantFromSubdomain(request.getServerName());
        }

        if (tenantId != null && !tenantId.isEmpty()) {
            TenantContext.setTenantId(tenantId);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        TenantContext.clear();
    }

    private String extractTenantFromSubdomain(String serverName) {
        if (serverName == null || serverName.isEmpty()) {
            return null;
        }

        String[] parts = serverName.split("\\.");
        if (parts.length > 1 && !parts[0].equals("localhost") && !parts[0].equals("www")) {
            return parts[0];
        }

        return null;
    }

}
