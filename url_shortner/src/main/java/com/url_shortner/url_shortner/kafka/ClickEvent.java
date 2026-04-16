package com.url_shortner.url_shortner.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickEvent {
    private String shortCode;
    private String originalUrl;
    private String userId;
    private LocalDateTime clickedAt;
}