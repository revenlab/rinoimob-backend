package com.rinoimob.service.auth;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class TokenService {
    private final RedisTemplate<String, String> stringRedisTemplate;

    public TokenService(RedisTemplate<String, String> stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Records the issued-at time for tokens of a tenant.
     * Used for immediate invalidation of all tokens when permissions change.
     */
    public void recordTenantTokenIssueTime(UUID tenantId, long issuedAtMs) {
        String key = "tenant:" + tenantId + ":last_valid_token_issued_at";
        stringRedisTemplate.opsForValue().set(key, String.valueOf(issuedAtMs));
    }

    /**
     * Gets the last valid token issued time for a tenant.
     * Tokens issued before this time are invalid.
     * Returns current time if never set (all tokens become invalid).
     */
    public long getLastValidTokenIssuedTime(UUID tenantId) {
        String key = "tenant:" + tenantId + ":last_valid_token_issued_at";
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            return System.currentTimeMillis();
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return System.currentTimeMillis();
        }
    }

    /**
     * Validates if a token is valid for a tenant.
     * Token is valid if issued AFTER the last valid issue time.
     */
    public boolean isTokenValidForTenant(UUID tenantId, long tokenIssuedAtMs) {
        long lastValidTime = getLastValidTokenIssuedTime(tenantId);
        return tokenIssuedAtMs >= lastValidTime;
    }

    /**
     * Invalidates all tokens for a tenant by setting the last valid issue time to now.
     * Called when role changes or user is deactivated.
     */
    public void invalidateTenantTokens(UUID tenantId) {
        recordTenantTokenIssueTime(tenantId, System.currentTimeMillis());
    }
}
