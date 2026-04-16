package com.url_shortner.url_shortner.ratelimit;

import com.url_shortner.url_shortner.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        String key = resolveKey(request);

        if (!rateLimiterService.isAllowed(key)) {
            throw new RateLimitExceededException(key);
        }

        return true;
    }

    private String resolveKey(HttpServletRequest request) {
        // use userId header if present, fall back to IP
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return "user:" + userId;
        }
        return "ip:" + getClientIp(request);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim(); // first IP in chain
        }
        return request.getRemoteAddr();
    }
}