package com.url_shortner.url_shortner.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private static final String URL_CACHE_PREFIX = "url:";

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.cache-ttl-seconds}")
    private long cacheTtlSeconds;

    // ─── Store original URL in Redis ──────────────────────────────────────────

    public void cacheUrl(String shortCode, String originalUrl) {
        String key = buildKey(shortCode);
        redisTemplate.opsForValue().set(key, originalUrl, cacheTtlSeconds, TimeUnit.SECONDS);
        log.info("Cached URL in Redis: {} -> {}", shortCode, originalUrl);
    }

    // ─── Retrieve original URL from Redis ─────────────────────────────────────

    public String getCachedUrl(String shortCode) {
        String key = buildKey(shortCode);
        String value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            log.info("Cache HIT for short code: {}", shortCode);
        } else {
            log.info("Cache MISS for short code: {}", shortCode);
        }
        return value;
    }

    // ─── Remove from cache (e.g. when URL expires) ────────────────────────────

    public void evictUrl(String shortCode) {
        String key = buildKey(shortCode);
        redisTemplate.delete(key);
        log.info("Evicted from cache: {}", shortCode);
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private String buildKey(String shortCode) {
        return URL_CACHE_PREFIX + shortCode;
    }
}