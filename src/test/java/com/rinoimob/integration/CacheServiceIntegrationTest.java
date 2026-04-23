package com.rinoimob.integration;

import com.rinoimob.service.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Cache Service Integration Tests")
class CacheServiceIntegrationTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new CacheService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Should set and get value from cache")
    void testCacheSetAndGet() {
        String key = "test:key";
        String value = "test:value";
        when(valueOperations.get(key)).thenReturn(value);

        cacheService.set(key, value);
        Object retrieved = cacheService.get(key);

        assertThat(retrieved).isEqualTo(value);
    }

    @Test
    @DisplayName("Should set value with expiration")
    void testCacheWithExpiration() {
        String key = "expiring:key";
        String value = "expiring:value";
        when(valueOperations.get(key)).thenReturn(value);
        when(redisTemplate.getExpire(key, TimeUnit.SECONDS)).thenReturn(10L);

        cacheService.set(key, value, 10, TimeUnit.SECONDS);
        Object retrieved = cacheService.get(key);

        assertThat(retrieved).isEqualTo(value);

        long expire = cacheService.getExpire(key);
        assertThat(expire).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should delete key from cache")
    void testCacheDelete() {
        String key = "delete:key";
        String value = "delete:value";
        when(valueOperations.get(key)).thenReturn(null);

        cacheService.set(key, value);
        cacheService.delete(key);

        Object retrieved = cacheService.get(key);
        assertThat(retrieved).isNull();
    }
}
