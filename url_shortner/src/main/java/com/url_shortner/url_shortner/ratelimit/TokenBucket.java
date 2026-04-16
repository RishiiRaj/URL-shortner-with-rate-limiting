package com.url_shortner.url_shortner.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenBucket {
    private double tokens;
    private long lastRefillTimeMs;
}