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
     * Records the minimum valid token issued-at time for a tenant.
     * All tokens issued BEFORE this time are invalid for the tenant.
     * Used when: role permissions change, affects all users in tenant.
     */
    public void recordTenantMinValidTokenIssuedTime(UUID tenantId, long issuedAtMs) {
        String key = "tenant:" + tenantId + ":min_valid_token_issued_at";
        stringRedisTemplate.opsForValue().set(key, String.valueOf(issuedAtMs));
    }

    /**
     * Records the minimum valid token issued-at time for a specific user.
     * All tokens issued BEFORE this time are invalid for this user.
     * Used when: user is deactivated, user logs out, user permissions change.
     */
    public void recordUserMinValidTokenIssuedTime(UUID userId, long issuedAtMs) {
        String key = "user:" + userId + ":min_valid_token_issued_at";
        stringRedisTemplate.opsForValue().set(key, String.valueOf(issuedAtMs));
    }

    /**
     * Gets the minimum valid token issued-at time for a tenant.
     * Returns 0 if never set (all tokens are valid).
     */
    public long getTenantMinValidTokenIssuedTime(UUID tenantId) {
        String key = "tenant:" + tenantId + ":min_valid_token_issued_at";
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Gets the minimum valid token issued-at time for a specific user.
     * Returns 0 if never set (all tokens are valid).
     */
    public long getUserMinValidTokenIssuedTime(UUID userId) {
        String key = "user:" + userId + ":min_valid_token_issued_at";
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Validates if a token is valid for a user in a tenant.
     * Token is valid if issued AFTER both tenant's and user's minimum valid times.
     */
    public boolean isTokenValidForTenant(UUID tenantId, UUID userId, long tokenIssuedAtMs) {
        long tenantMinValidTime = getTenantMinValidTokenIssuedTime(tenantId);
        long userMinValidTime = getUserMinValidTokenIssuedTime(userId);
        
        return tokenIssuedAtMs >= tenantMinValidTime && tokenIssuedAtMs >= userMinValidTime;
    }

    /**
     * Invalidates all tokens for a tenant by setting the minimum valid issued-at time to now.
     * Called when role permissions change (affects all users).
     */
    public void invalidateAllTenantTokens(UUID tenantId) {
        recordTenantMinValidTokenIssuedTime(tenantId, System.currentTimeMillis());
    }

    /**
     * Invalidates all tokens for a specific user by setting the minimum valid issued-at time to now.
     * Called when user is deactivated or logs out.
     */
    public void invalidateUserTokens(UUID userId) {
        recordUserMinValidTokenIssuedTime(userId, System.currentTimeMillis());
    }
}
