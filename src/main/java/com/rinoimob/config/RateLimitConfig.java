package com.rinoimob.config;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import io.github.bucket4j.Bandwidth;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class RateLimitConfig {

    private static final Map<String, Bucket> cache = new HashMap<>();

    @Bean
    public RateLimitConfig rateLimitConfig() {
        return this;
    }

    public Bucket resolveBucket(String clientId) {
        return cache.computeIfAbsent(clientId, k -> createNewBucket());
    }

    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1)));
        return Bucket4j.builder()
                .addLimit(limit)
                .build();
    }

    public boolean allowRequest(String clientId) {
        Bucket bucket = resolveBucket(clientId);
        return bucket.tryConsume(1);
    }

    public long getRemainingTokens(String clientId) {
        Bucket bucket = resolveBucket(clientId);
        return bucket.getAvailableTokens();
    }
}
