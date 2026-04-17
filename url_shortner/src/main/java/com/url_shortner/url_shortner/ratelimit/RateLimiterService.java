package com.url_shortner.url_shortner.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class RateLimiterService {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.rate-limit.max-requests}")
    private int maxRequests;

    @Value("${app.rate-limit.window-seconds}")
    private int windowSeconds;

    private static final String KEY_PREFIX = "rate_limit:";

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
        RATE_LIMIT_SCRIPT.setScriptText(
                "local key = KEYS[1] " +
                        "local now = tonumber(ARGV[1]) " +
                        "local max_tokens = tonumber(ARGV[2]) " +
                        "local refill_rate = tonumber(ARGV[3]) " +
                        "local ttl = tonumber(ARGV[4]) " +

                        "local bucket = redis.call('HMGET', key, 'tokens', 'last_refill_time') " +
                        "local tokens = tonumber(bucket[1]) " +
                        "local last_refill_time = tonumber(bucket[2]) " +

                        "if tokens == nil then " +
                        "  redis.call('HSET', key, 'tokens', max_tokens - 1, 'last_refill_time', now) " +
                        "  redis.call('EXPIRE', key, ttl) " +
                        "  return 1 " +
                        "end " +

                        "local elapsed = (now - last_refill_time) / 1000.0 " +
                        "local new_tokens = math.min(max_tokens, tokens + elapsed * refill_rate) " +

                        "if new_tokens < 1.0 then " +
                        "  return 0 " +
                        "end " +

                        "redis.call('HSET', key, 'tokens', new_tokens - 1, 'last_refill_time', now) " +
                        "redis.call('EXPIRE', key, ttl) " +
                        "return 1");
    }

    private final MeterRegistry meterRegistry;

    public RateLimiterService(StringRedisTemplate stringRedisTemplate,
            MeterRegistry meterRegistry) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.meterRegistry = meterRegistry;
    }

    public boolean isAllowed(String key) {
        String redisKey = KEY_PREFIX + key;
        long now = System.currentTimeMillis();
        double refillRate = (double) maxRequests / windowSeconds;
        int ttl = windowSeconds * 2;

        List<String> keys = Collections.singletonList(redisKey);
        Long result = stringRedisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                keys,
                String.valueOf(now),
                String.valueOf(maxRequests),
                String.valueOf(refillRate),
                String.valueOf(ttl));

        boolean allowed = result != null && result == 1L;

        if (allowed) {
            log.info("Request allowed for key={}", key);
        } else {
            meterRegistry.counter("url.ratelimit.exceeded",
                    "key", key).increment(); 
            log.warn("Rate limit exceeded for key={}", key);
        }

        return allowed;
    }
}