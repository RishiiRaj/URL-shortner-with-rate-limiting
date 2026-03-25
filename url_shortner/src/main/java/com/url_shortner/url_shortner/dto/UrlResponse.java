package com.url_shortner.url_shortner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlResponse {

    private String shortCode;
    private String shortUrl;
    private String originalUrl;
    private String userId;
    private Long clickCount;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}