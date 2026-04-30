package com.rinoimob.service.auth;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TokenService {
    private final RedisTemplate<String, String> stringRedisTemplate;

    public TokenService(RedisTemplate<String, String> stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void store(String jti, UUID userId, long ttlSeconds) {
        String tokenKey = "token:" + jti;
        String userTokensKey = "user_tokens:" + userId;
        stringRedisTemplate.opsForValue().set(tokenKey, userId.toString(), ttlSeconds, TimeUnit.SECONDS);
        stringRedisTemplate.opsForSet().add(userTokensKey, jti);
        stringRedisTemplate.expire(userTokensKey, ttlSeconds, TimeUnit.SECONDS);
    }

    public boolean isValid(String jti) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey("token:" + jti));
    }

    public void revoke(String jti) {
        String tokenKey = "token:" + jti;
        String userId = stringRedisTemplate.opsForValue().get(tokenKey);
        if (userId != null) {
            stringRedisTemplate.delete(tokenKey);
            stringRedisTemplate.opsForSet().remove("user_tokens:" + userId, jti);
        }
    }

    public void revokeAllForUser(UUID userId) {
        String userTokensKey = "user_tokens:" + userId;
        Set<String> jtis = stringRedisTemplate.opsForSet().members(userTokensKey);
        if (jtis != null) {
            for (String jti : jtis) {
                stringRedisTemplate.delete("token:" + jti);
            }
        }
        stringRedisTemplate.delete(userTokensKey);
    }
}
