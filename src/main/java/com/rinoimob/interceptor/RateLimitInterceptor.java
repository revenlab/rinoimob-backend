package com.rinoimob.interceptor;

import com.rinoimob.config.RateLimitConfig;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.logging.Logger;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger logger = Logger.getLogger(RateLimitInterceptor.class.getName());
    private final RateLimitConfig rateLimitConfig;

    public RateLimitInterceptor(RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientId = getClientId(request);

        if (!rateLimitConfig.allowRequest(clientId)) {
            logger.warning("Rate limit exceeded for client: " + clientId);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Too many requests. Rate limit exceeded.\"}");
            return false;
        }

        long remainingTokens = rateLimitConfig.getRemainingTokens(clientId);
        response.addHeader("X-RateLimit-Remaining", String.valueOf(remainingTokens));

        return true;
    }

    private String getClientId(HttpServletRequest request) {
        String clientId = request.getHeader("X-Client-ID");
        if (clientId == null || clientId.isEmpty()) {
            clientId = request.getRemoteAddr();
        }
        return clientId;
    }
}
