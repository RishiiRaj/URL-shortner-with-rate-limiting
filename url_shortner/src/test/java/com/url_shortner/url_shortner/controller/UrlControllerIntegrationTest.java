package com.url_shortner.url_shortner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.url_shortner.url_shortner.dto.UrlRequest;
import com.url_shortner.url_shortner.ratelimit.RateLimiterService;
import com.url_shortner.url_shortner.service.UrlService;
import com.url_shortner.url_shortner.dto.UrlResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UrlController.class)
class UrlControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UrlService urlService;

    @MockBean
    private RateLimiterService rateLimiterService;

    @Test
    void shortenUrl_validRequest_returns201() throws Exception {
        UrlRequest request = new UrlRequest();
        request.setOriginalUrl("https://www.google.com");

        UrlResponse response = UrlResponse.builder()
                .shortCode("abc1234")
                .shortUrl("http://localhost:8080/abc1234")
                .originalUrl("https://www.google.com")
                .clickCount(0L)
                .createdAt(LocalDateTime.now())
                .build();

        when(rateLimiterService.isAllowed(any())).thenReturn(true);
        when(urlService.shortenUrl(any())).thenReturn(response);

        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("abc1234"))
                .andExpect(jsonPath("$.originalUrl").value("https://www.google.com"));
    }

    @Test
    void shortenUrl_rateLimitExceeded_returns429() throws Exception {
        UrlRequest request = new UrlRequest();
        request.setOriginalUrl("https://www.google.com");

        when(rateLimiterService.isAllowed(any())).thenReturn(false);

        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void redirect_validShortCode_returns302() throws Exception {
        when(rateLimiterService.isAllowed(any())).thenReturn(true);
        when(urlService.resolveShortCode("abc1234")).thenReturn("https://www.google.com");

        mockMvc.perform(get("/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://www.google.com"));
    }

    @Test
    void getStats_validShortCode_returns200() throws Exception {
        UrlResponse response = UrlResponse.builder()
                .shortCode("abc1234")
                .shortUrl("http://localhost:8080/abc1234")
                .originalUrl("https://www.google.com")
                .clickCount(5L)
                .createdAt(LocalDateTime.now())
                .build();

        when(rateLimiterService.isAllowed(any())).thenReturn(true);
        when(urlService.getStats("abc1234")).thenReturn(response);

        mockMvc.perform(get("/api/stats/abc1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(5));
    }
}