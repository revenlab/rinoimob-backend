package com.rinoimob.config;

import com.rinoimob.interceptor.TenantInterceptor;
import com.rinoimob.interceptor.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(TenantInterceptor tenantInterceptor, RateLimitInterceptor rateLimitInterceptor) {
        this.tenantInterceptor = tenantInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns(
                        "/api/v1/auth/identify",
                        "/api/v1/auth/login",
                        "/api/v1/auth/select-tenant",
                        "/api/v1/auth/signup",
                        "/api/v1/auth/register",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password"
                );
        registry.addInterceptor(tenantInterceptor);
    }

}
