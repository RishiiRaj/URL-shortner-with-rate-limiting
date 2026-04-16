package com.url_shortner.url_shortner.repository;

import com.url_shortner.url_shortner.model.ClickAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickAnalyticsRepository extends JpaRepository<ClickAnalytics, Long> {
}