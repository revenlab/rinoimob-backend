package com.rinoimob.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
            log.debug("Cache set for key: {}", key);
        } catch (Exception e) {
            log.warn("Failed to set cache for key: {}", key, e);
        }
    }

    @Transactional
    public void set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
            log.debug("Cache set for key: {}", key);
        } catch (Exception e) {
            log.warn("Failed to set cache for key: {}", key, e);
        }
    }

    public Object get(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (Objects.nonNull(value)) {
                log.debug("Cache hit for key: {}", key);
                return value;
            }
            log.debug("Cache miss for key: {}", key);
            return null;
        } catch (Exception e) {
            log.warn("Failed to get cache for key: {}", key, e);
            return null;
        }
    }

    @Transactional
    public void delete(String key) {
        try {
            Boolean deleted = redisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                log.debug("Cache deleted for key: {}", key);
            }
        } catch (Exception e) {
            log.warn("Failed to delete cache for key: {}", key, e);
        }
    }

    @Transactional
    public void invalidate(String pattern) {
        try {
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                Long deleted = redisTemplate.delete(keys);
                log.debug("Invalidated {} cache entries for pattern: {}", deleted, pattern);
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate cache for pattern: {}", pattern, e);
        }
    }

    public boolean exists(String key) {
        try {
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("Failed to check cache existence for key: {}", key, e);
            return false;
        }
    }

    public long getExpire(String key) {
        try {
            Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return Objects.nonNull(expire) ? expire : -1;
        } catch (Exception e) {
            log.warn("Failed to get cache expiry for key: {}", key, e);
            return -1;
        }
    }
}
