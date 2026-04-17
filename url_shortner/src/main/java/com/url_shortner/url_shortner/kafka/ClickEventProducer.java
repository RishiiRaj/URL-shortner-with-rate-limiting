package com.url_shortner.url_shortner.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class ClickEventProducer {

    private final KafkaTemplate<String, ClickEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.topic.click-events}")
    private String topic;

    public ClickEventProducer(KafkaTemplate<String, ClickEvent> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Async
    public void publishClickEvent(String shortCode, String originalUrl, String userId) {
        ClickEvent event = ClickEvent.builder()
                .shortCode(shortCode)
                .originalUrl(originalUrl)
                .userId(userId)
                .clickedAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(topic, shortCode, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish click event for shortCode={}: {}", shortCode, ex.getMessage());
                    } else {
                        meterRegistry.counter("url.click.published").increment();
                        log.info("Click event published for shortCode={}, partition={}, offset={}",
                                shortCode,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}