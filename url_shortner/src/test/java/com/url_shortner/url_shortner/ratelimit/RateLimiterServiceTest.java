package com.url_shortner.url_shortner.ratelimit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService(stringRedisTemplate, new SimpleMeterRegistry());
        ReflectionTestUtils.setField(rateLimiterService, "maxRequests", 10);
        ReflectionTestUtils.setField(rateLimiterService, "windowSeconds", 60);
    }

    @Test
    void isAllowed_whenRedisReturns1_returnsTrue() {
        when(stringRedisTemplate.execute(any(), any(List.class), any(Object[].class)))
                .thenReturn(1L);

        boolean result = rateLimiterService.isAllowed("user:testuser");

        assertThat(result).isTrue();
    }

    @Test
    void isAllowed_whenRedisReturns0_returnsFalse() {
        when(stringRedisTemplate.execute(any(), any(List.class), any(Object[].class)))
                .thenReturn(0L);

        boolean result = rateLimiterService.isAllowed("user:testuser");

        assertThat(result).isFalse();
    }

    @Test
    void isAllowed_whenRedisReturnsNull_returnsFalse() {
        when(stringRedisTemplate.execute(any(), any(List.class), any(Object[].class)))
                .thenReturn(null);

        boolean result = rateLimiterService.isAllowed("user:testuser");

        assertThat(result).isFalse();
    }
}