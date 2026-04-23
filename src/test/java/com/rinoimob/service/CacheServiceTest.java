package com.rinoimob.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheServiceTest {

    private CacheService cacheService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @BeforeEach
    void setUp() {
        cacheService = new CacheService(redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testSetAndGet() {
        String key = "testKey";
        Object value = "testValue";
        when(valueOperations.get(key)).thenReturn(value);

        cacheService.set(key, value);
        Object retrieved = cacheService.get(key);

        assertThat(retrieved).isEqualTo(value);
    }

    @Test
    void testSetWithTimeout() {
        String key = "timeoutKey";
        Object value = "timeoutValue";
        when(valueOperations.get(key)).thenReturn(value);
        when(redisTemplate.getExpire(key, TimeUnit.SECONDS)).thenReturn(60L);

        cacheService.set(key, value, 1, TimeUnit.SECONDS);
        Object retrieved = cacheService.get(key);

        assertThat(retrieved).isEqualTo(value);

        long expire = cacheService.getExpire(key);
        assertThat(expire).isGreaterThan(0);
    }

    @Test
    void testDelete() {
        String key = "deleteKey";
        when(valueOperations.get(key)).thenReturn(null);

        cacheService.set(key, "deleteValue");

        cacheService.delete(key);
        Object retrieved = cacheService.get(key);

        assertThat(retrieved).isNull();
    }

    @Test
    void testExists() {
        String key = "existsKey";
        when(redisTemplate.hasKey(key)).thenReturn(true, false);

        cacheService.set(key, "existsValue");

        boolean exists = cacheService.exists(key);
        assertThat(exists).isTrue();

        cacheService.delete(key);
        exists = cacheService.exists(key);
        assertThat(exists).isFalse();
    }

    @Test
    void testInvalidate() {
        when(redisTemplate.keys("prefix:*")).thenReturn(Set.of("prefix:key1", "prefix:key2"));
        when(redisTemplate.delete(anyCollection())).thenReturn(2L);
        when(valueOperations.get("prefix:key1")).thenReturn(null);
        when(valueOperations.get("prefix:key2")).thenReturn(null);
        when(valueOperations.get("other:key")).thenReturn("value3");

        cacheService.set("prefix:key1", "value1");
        cacheService.set("prefix:key2", "value2");
        cacheService.set("other:key", "value3");

        cacheService.invalidate("prefix:*");

        assertThat(cacheService.get("prefix:key1")).isNull();
        assertThat(cacheService.get("prefix:key2")).isNull();
        assertThat(cacheService.get("other:key")).isNotNull();
    }

    @Test
    void testGetNonExistentKey() {
        Object retrieved = cacheService.get("nonExistentKey");
        assertThat(retrieved).isNull();
    }

    @Test
    void testGetExpireForNonExistentKey() {
        when(redisTemplate.getExpire("nonExistentKey", TimeUnit.SECONDS)).thenReturn(null);

        long expire = cacheService.getExpire("nonExistentKey");
        assertThat(expire).isEqualTo(-1);
    }
}
