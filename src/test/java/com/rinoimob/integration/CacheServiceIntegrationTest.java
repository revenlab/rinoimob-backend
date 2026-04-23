package com.rinoimob.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Cache Service Integration Tests")
class CacheServiceIntegrationTest extends IntegrationTestBase {

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("Should set and get value from cache")
    void testCacheSetAndGet() {
        if (redisTemplate == null) {
            return;
        }

        String key = "test:key";
        String value = "test:value";

        redisTemplate.opsForValue().set(key, value);
        Object retrieved = redisTemplate.opsForValue().get(key);

        assertThat(retrieved).isEqualTo(value);

        redisTemplate.delete(key);
    }

    @Test
    @DisplayName("Should set value with expiration")
    void testCacheWithExpiration() {
        if (redisTemplate == null) {
            return;
        }

        String key = "expiring:key";
        String value = "expiring:value";

        redisTemplate.opsForValue().set(key, value, 10, TimeUnit.SECONDS);
        Object retrieved = redisTemplate.opsForValue().get(key);

        assertThat(retrieved).isEqualTo(value);

        Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(expire).isGreaterThan(0);

        redisTemplate.delete(key);
    }

    @Test
    @DisplayName("Should delete key from cache")
    void testCacheDelete() {
        if (redisTemplate == null) {
            return;
        }

        String key = "delete:key";
        String value = "delete:value";

        redisTemplate.opsForValue().set(key, value);
        redisTemplate.delete(key);

        Object retrieved = redisTemplate.opsForValue().get(key);
        assertThat(retrieved).isNull();
    }
}
