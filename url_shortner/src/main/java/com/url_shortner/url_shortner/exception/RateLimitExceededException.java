
package com.url_shortner.url_shortner.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String key) {
        super("Rate limit exceeded for: " + key);
    }
}