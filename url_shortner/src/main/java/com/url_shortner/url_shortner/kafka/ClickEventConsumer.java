package com.url_shortner.url_shortner.kafka;

import com.url_shortner.url_shortner.model.ClickAnalytics;
import com.url_shortner.url_shortner.repository.ClickAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClickEventConsumer {

    private final ClickAnalyticsRepository clickAnalyticsRepository;

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
}