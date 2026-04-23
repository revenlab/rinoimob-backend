package com.rinoimob.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Service
public class CacheService {

    private static final Logger logger = Logger.getLogger(CacheService.class.getName());
    private final RedisTemplate<String, Object> redisTemplate;

    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
            logger.fine("Cache set for key: " + key);
        } catch (Exception e) {
            logger.warning("Failed to set cache for key: " + key);
        }
    }

    @Transactional
    public void set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
            logger.fine("Cache set for key: " + key);
        } catch (Exception e) {
            logger.warning("Failed to set cache for key: " + key);
        }
    }

    public Object get(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (Objects.nonNull(value)) {
                logger.fine("Cache hit for key: " + key);
                return value;
            }
            logger.fine("Cache miss for key: " + key);
            return null;
        } catch (Exception e) {
            logger.warning("Failed to get cache for key: " + key);
            return null;
        }
    }

    @Transactional
    public void delete(String key) {
        try {
            Boolean deleted = redisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                logger.fine("Cache deleted for key: " + key);
            }
        } catch (Exception e) {
            logger.warning("Failed to delete cache for key: " + key);
        }
    }

    @Transactional
    public void invalidate(String pattern) {
        try {
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                Long deleted = redisTemplate.delete(keys);
                logger.fine("Invalidated " + deleted + " cache entries for pattern: " + pattern);
            }
        } catch (Exception e) {
            logger.warning("Failed to invalidate cache for pattern: " + pattern);
        }
    }

    public boolean exists(String key) {
        try {
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            logger.warning("Failed to check cache existence for key: " + key);
            return false;
        }
    }

    public long getExpire(String key) {
        try {
            Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return Objects.nonNull(expire) ? expire : -1;
        } catch (Exception e) {
            logger.warning("Failed to get cache expiry for key: " + key);
            return -1;
        }
    }
}
