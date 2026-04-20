package com.url_shortner.url_shortner.service;

import com.url_shortner.url_shortner.dto.UrlRequest;
import com.url_shortner.url_shortner.dto.UrlResponse;
import com.url_shortner.url_shortner.exception.UrlNotFoundException;
import com.url_shortner.url_shortner.kafka.ClickEventProducer;
import com.url_shortner.url_shortner.model.UrlEntity;
import com.url_shortner.url_shortner.repository.UrlRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private CacheService cacheService;

    @Mock
    private ClickEventProducer clickEventProducer;

    private MeterRegistry meterRegistry;
    private UrlService urlService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        urlService = new UrlService(urlRepository, cacheService, clickEventProducer, meterRegistry);
        ReflectionTestUtils.setField(urlService, "baseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(urlService, "shortCodeLength", 7);
    }

    // ─── shortenUrl ───────────────────────────────────────────────────────────

    @Test
    void shortenUrl_newUrl_savesAndReturnsResponse() {
        UrlRequest request = new UrlRequest();
        request.setOriginalUrl("https://www.google.com");

        when(urlRepository.findFirstByOriginalUrl(any())).thenReturn(Optional.empty());
        when(urlRepository.existsByShortCode(any())).thenReturn(false);
        when(urlRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UrlResponse response = urlService.shortenUrl(request);

        assertThat(response.getOriginalUrl()).isEqualTo("https://www.google.com");
        assertThat(response.getShortCode()).hasSize(7);
        assertThat(response.getShortUrl()).startsWith("http://localhost:8080/");
        verify(urlRepository).save(any(UrlEntity.class));
        verify(cacheService).cacheUrl(anyString(), eq("https://www.google.com"));
    }

    @Test
    void shortenUrl_duplicateUrl_returnsExistingShortCode() {
        UrlRequest request = new UrlRequest();
        request.setOriginalUrl("https://www.google.com");

        UrlEntity existing = UrlEntity.builder()
                .shortCode("abc1234")
                .originalUrl("https://www.google.com")
                .clickCount(0L)
                .build();

        when(urlRepository.findFirstByOriginalUrl("https://www.google.com"))
                .thenReturn(Optional.of(existing));

        UrlResponse response = urlService.shortenUrl(request);

        assertThat(response.getShortCode()).isEqualTo("abc1234");
        verify(urlRepository, never()).save(any()); // no new row created
    }

    @Test
    void shortenUrl_withExpiryDays_setsExpiresAt() {
        UrlRequest request = new UrlRequest();
        request.setOriginalUrl("https://www.example.com");
        request.setExpiryDays(30);

        when(urlRepository.findFirstByOriginalUrl(any())).thenReturn(Optional.empty());
        when(urlRepository.existsByShortCode(any())).thenReturn(false);
        when(urlRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UrlResponse response = urlService.shortenUrl(request);

        assertThat(response.getExpiresAt()).isNotNull();
        assertThat(response.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    // ─── resolveShortCode ─────────────────────────────────────────────────────

    @Test
    void resolveShortCode_cacheHit_returnsCachedUrl() {
        when(cacheService.getCachedUrl("abc1234")).thenReturn("https://www.google.com");

        String result = urlService.resolveShortCode("abc1234");

        assertThat(result).isEqualTo("https://www.google.com");
        verify(urlRepository, never()).findByShortCode(any()); // DB not hit
        verify(clickEventProducer).publishClickEvent(eq("abc1234"), eq("https://www.google.com"), isNull());
    }

    @Test
    void resolveShortCode_cacheMiss_queriesDbAndCaches() {
        UrlEntity entity = UrlEntity.builder()
                .shortCode("abc1234")
                .originalUrl("https://www.google.com")
                .clickCount(0L)
                .build();

        when(cacheService.getCachedUrl("abc1234")).thenReturn(null);
        when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(entity));

        String result = urlService.resolveShortCode("abc1234");

        assertThat(result).isEqualTo("https://www.google.com");
        verify(cacheService).cacheUrl("abc1234", "https://www.google.com"); // cached after miss
        verify(clickEventProducer).publishClickEvent(eq("abc1234"), eq("https://www.google.com"), isNull());
    }

    @Test
    void resolveShortCode_notFound_throwsUrlNotFoundException() {
        when(cacheService.getCachedUrl("invalid")).thenReturn(null);
        when(urlRepository.findByShortCode("invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.resolveShortCode("invalid"))
                .isInstanceOf(UrlNotFoundException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void resolveShortCode_expiredUrl_throwsUrlNotFoundException() {
        UrlEntity expired = UrlEntity.builder()
                .shortCode("abc1234")
                .originalUrl("https://www.google.com")
                .clickCount(0L)
                .expiresAt(LocalDateTime.now().minusDays(1)) // expired yesterday
                .build();

        when(cacheService.getCachedUrl("abc1234")).thenReturn(null);
        when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> urlService.resolveShortCode("abc1234"))
                .isInstanceOf(UrlNotFoundException.class)
                .hasMessageContaining("expired");
    }
}