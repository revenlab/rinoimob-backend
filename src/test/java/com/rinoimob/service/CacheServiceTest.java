package com.rinoimob.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CacheServiceTest {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    void testSetAndGet() {
        String key = "testKey";
        Object value = "testValue";

        cacheService.set(key, value);
        Object retrieved = cacheService.get(key);

        assertThat(retrieved).isEqualTo(value);
    }

    @Test
    void testSetWithTimeout() {
        String key = "timeoutKey";
        Object value = "timeoutValue";

        cacheService.set(key, value, 1, TimeUnit.SECONDS);
        Object retrieved = cacheService.get(key);

        assertThat(retrieved).isEqualTo(value);

        long expire = cacheService.getExpire(key);
        assertThat(expire).isGreaterThan(0);
    }

    @Test
    void testDelete() {
        String key = "deleteKey";
        cacheService.set(key, "deleteValue");

        cacheService.delete(key);
        Object retrieved = cacheService.get(key);

        assertThat(retrieved).isNull();
    }

    @Test
    void testExists() {
        String key = "existsKey";
        cacheService.set(key, "existsValue");

        boolean exists = cacheService.exists(key);
        assertThat(exists).isTrue();

        cacheService.delete(key);
        exists = cacheService.exists(key);
        assertThat(exists).isFalse();
    }

    @Test
    void testInvalidate() {
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
        long expire = cacheService.getExpire("nonExistentKey");
        assertThat(expire).isEqualTo(-1);
    }
}
