package com.url_shortner.url_shortner.kafka;

import com.url_shortner.url_shortner.model.ClickAnalytics;
import com.url_shortner.url_shortner.repository.ClickAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClickEventConsumer {

    private final ClickAnalyticsRepository clickAnalyticsRepository;

    @RetryableTopic(attempts = "3", // original + 2 retries
            backoff = @Backoff(delay = 1000, multiplier = 2), // 1s, 2s backoff
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE, dltTopicSuffix = "-dlt")
    @KafkaListener(topics = "${kafka.topic.click-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(ClickEvent event) {
        log.info("Consumed click event for shortCode={}", event.getShortCode());

        ClickAnalytics analytics = ClickAnalytics.builder()
                .shortCode(event.getShortCode())
                .originalUrl(event.getOriginalUrl())
                .userId(event.getUserId())
                .clickedAt(event.getClickedAt())
                .build();

        clickAnalyticsRepository.save(analytics);
        log.info("Persisted click analytics for shortCode={}", event.getShortCode());
    }

    // ─── Dead Letter Topic handler ────────────────────────────────────────────
    @DltHandler
    public void handleDlt(ClickEvent event) {
        log.error("DEAD LETTER: Failed to process click event after all retries. " +
                "shortCode={}, originalUrl={}", event.getShortCode(), event.getOriginalUrl());
        // in production: send alert, push to monitoring, store for manual reprocessing
    }
}