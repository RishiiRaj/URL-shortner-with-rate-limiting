package com.url_shortner.url_shortner.ratelimit;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class RateLimiterService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.rate-limit.max-requests}")
    private int maxRequests;

    @Value("${app.rate-limit.window-seconds}")
    private int windowSeconds;

    private static final String KEY_PREFIX = "rate_limit:";

    public RateLimiterService(@Qualifier("objectRedisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(String key) {
        String redisKey = KEY_PREFIX + key;
        long now = System.currentTimeMillis();

        // get existing bucket from Redis
        TokenBucket bucket = (TokenBucket) redisTemplate.opsForValue().get(redisKey);

        if (bucket == null) {
            // first request — create a full bucket with one token already consumed
            bucket = new TokenBucket(maxRequests - 1, now);
            redisTemplate.opsForValue().set(redisKey, bucket, Duration.ofSeconds(windowSeconds * 2));
            log.info("New bucket created for key={}, tokens={}", key, bucket.getTokens());
            return true;
        }

        // refill tokens based on elapsed time
        double refillRate = (double) maxRequests / windowSeconds; // tokens per ms
        long elapsedMs = now - bucket.getLastRefillTimeMs();
        double tokensToAdd = elapsedMs * refillRate / 1000.0;

        double newTokens = Math.min(maxRequests, bucket.getTokens() + tokensToAdd);

        if (newTokens < 1.0) {
            log.warn("Rate limit exceeded for key={}, tokens={}", key, newTokens);
            return false;
        }

        // consume one token and save back to Redis
        bucket.setTokens(newTokens - 1);
        bucket.setLastRefillTimeMs(now);
        redisTemplate.opsForValue().set(redisKey, bucket, Duration.ofSeconds(windowSeconds * 2));

        log.info("Request allowed for key={}, remaining tokens={}", key, bucket.getTokens());
        return true;
    }
}