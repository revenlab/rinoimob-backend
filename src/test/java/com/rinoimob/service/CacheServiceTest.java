package com.rinoimob.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new CacheService(redisTemplate, cacheManager);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testSet() {
        String key = "test-key";
        String value = "test-value";

        cacheService.set(key, value);

        verify(valueOperations, times(1)).set(key, value);
    }

    @Test
    void testSetWithTimeout() {
        String key = "test-key";
        String value = "test-value";
        long timeout = 300;
        TimeUnit unit = TimeUnit.SECONDS;

        cacheService.set(key, value, timeout, unit);

        verify(valueOperations, times(1)).set(key, value, timeout, unit);
    }

    @Test
    void testGet() {
        String key = "test-key";
        when(valueOperations.get(key)).thenReturn("test-value");

        Object result = cacheService.get(key);

        assertThat(result).isEqualTo("test-value");
        verify(valueOperations, times(1)).get(key);
    }

    @Test
    void testDelete() {
        String key = "test-key";

        cacheService.delete(key);

        verify(redisTemplate, times(1)).delete(key);
    }

    @Test
    void testHasKey() {
        String key = "test-key";
        when(redisTemplate.hasKey(key)).thenReturn(true);

        Boolean result = cacheService.hasKey(key);

        assertThat(result).isTrue();
        verify(redisTemplate, times(1)).hasKey(key);
    }

}
