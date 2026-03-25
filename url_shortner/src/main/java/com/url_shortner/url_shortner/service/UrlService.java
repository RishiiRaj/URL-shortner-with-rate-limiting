package com.url_shortner.url_shortner.service;

import com.url_shortner.url_shortner.dto.UrlRequest;
import com.url_shortner.url_shortner.dto.UrlResponse;
import com.url_shortner.url_shortner.exception.UrlNotFoundException;
import com.url_shortner.url_shortner.model.UrlEntity;
import com.url_shortner.url_shortner.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlService {

    private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int MAX_RETRIES = 5;

    private final UrlRepository urlRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.short-code-length}")
    private int shortCodeLength;

    // ─── Shorten a URL ───────────────────────────────────────────────────────

    @Transactional
    public UrlResponse shortenUrl(UrlRequest request) {
        String shortCode = generateUniqueShortCode();

        UrlEntity entity = UrlEntity.builder()
                .shortCode(shortCode)
                .originalUrl(request.getOriginalUrl())
                .userId(request.getUserId())
                .clickCount(0L)
                .expiresAt(request.getExpiryDays() != null
                        ? LocalDateTime.now().plusDays(request.getExpiryDays())
                        : null)
                .build();

        urlRepository.save(entity);
        log.info("Shortened URL: {} -> {}", request.getOriginalUrl(), shortCode);

        return toResponse(entity);
    }

    // ─── Resolve a short code to original URL ────────────────────────────────

    @Transactional
    public String resolveShortCode(String shortCode) {
        UrlEntity entity = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("Short code not found: " + shortCode));

        if (isExpired(entity)) {
            throw new UrlNotFoundException("Short URL has expired: " + shortCode);
        }

        urlRepository.incrementClickCount(shortCode);
        log.info("Resolved short code: {} -> {}", shortCode, entity.getOriginalUrl());

        return entity.getOriginalUrl();
    }

    // ─── Get URL stats ────────────────────────────────────────────────────────

    public UrlResponse getStats(String shortCode) {
        UrlEntity entity = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("Short code not found: " + shortCode));
        return toResponse(entity);
    }

    // ─── Base62 short code generation ────────────────────────────────────────

    private String generateUniqueShortCode() {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String code = generateShortCode();
            if (!urlRepository.existsByShortCode(code)) {
                return code;
            }
            log.warn("Short code collision detected, retrying... attempt {}", attempt + 1);
        }
        throw new RuntimeException("Failed to generate unique short code after " + MAX_RETRIES + " attempts");
    }

    private String generateShortCode() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder(shortCodeLength);
        for (int i = 0; i < shortCodeLength; i++) {
            sb.append(BASE62.charAt(random.nextInt(BASE62.length())));
        }
        return sb.toString();
    }

    private boolean isExpired(UrlEntity entity) {
        return entity.getExpiresAt() != null
                && LocalDateTime.now().isAfter(entity.getExpiresAt());
    }

    // ─── Mapper ───────────────────────────────────────────────────────────────

    private UrlResponse toResponse(UrlEntity entity) {
        return UrlResponse.builder()
                .shortCode(entity.getShortCode())
                .shortUrl(baseUrl + "/" + entity.getShortCode())
                .originalUrl(entity.getOriginalUrl())
                .userId(entity.getUserId())
                .clickCount(entity.getClickCount())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .build();
    }
}