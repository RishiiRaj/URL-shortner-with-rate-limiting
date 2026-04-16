# ⚡ Snip.ly — Distributed URL Shortener with Rate Limiting

A production-style URL shortening service built with Spring Boot, featuring Redis caching, async Kafka analytics, and token bucket rate limiting.

---

## Architecture

```
Client
  │
  ▼
Spring Boot App (Port 8080)
  │
  ├── POST /api/shorten
  │     └── Base62 encode → PostgreSQL → Redis cache
  │
  ├── GET /{shortCode}                          ← critical redirect path
  │     ├── Redis (cache hit → <10ms)
  │     ├── PostgreSQL (cache miss → fallback)
  │     └── Kafka producer (async, non-blocking) ──► click-events topic
  │                                                       │
  │                                                       ▼
  │                                               Kafka Consumer
  │                                                       │
  │                                                       ▼
  │                                               click_analytics table
  │
  └── RateLimitInterceptor (every request)
        └── Token Bucket (Redis-backed, per user/IP)
              └── Exceeded → 429 Too Many Requests
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3 |
| Database | PostgreSQL 15 |
| Cache | Redis 7 |
| Messaging | Apache Kafka (Confluent 7.5) |
| Infra | Docker Compose |
| Build | Maven |

---

## Features

- **Base62 URL shortening** — 7-character short codes with collision-resistant hash generation (56 billion combinations)
- **Redis caching** — cache-aside pattern, average redirect latency under 10ms for cached URLs
- **Async Kafka pipeline** — click events streamed to Kafka on every redirect without blocking the critical path
- **Click analytics** — every redirect persisted to `click_analytics` table via Kafka consumer
- **Token bucket rate limiting** — per user ID or IP, enforced via Spring interceptor (default: 10 requests / 60 seconds)
- **URL expiry** — optional TTL per shortened URL
- **Docker Compose** — full local stack with service isolation

---

## Running Locally

### Prerequisites
- Java 17+
- Maven
- Docker Desktop

### Steps

**1. Clone the repository**
```bash
git clone https://github.com/RishiiRaj/URL-shortner-with-rate-limiting.git
cd url-shortener
```

**2. Start all infrastructure services**
```bash
docker compose up -d
```

This starts PostgreSQL, Redis, Kafka, and Zookeeper.

**3. Wait 15-20 seconds** for Kafka and PostgreSQL to fully initialize.

**4. Run the Spring Boot app**
```bash
mvn spring-boot:run
```

**5. Open the UI**

Navigate to `http://localhost:8080` in your browser.

---

## API Endpoints

### Shorten a URL
```bash
POST /api/shorten
Content-Type: application/json
X-User-Id: user123   # optional, used for rate limiting

{
  "originalUrl": "https://www.example.com/very/long/url",
  "userId": "user123",       # optional
  "expiryDays": 30           # optional
}
```

**Response:**
```json
{
  "shortCode": "aB3kPqR",
  "shortUrl": "http://localhost:8080/aB3kPqR",
  "originalUrl": "https://www.example.com/very/long/url",
  "userId": "user123",
  "clickCount": 0,
  "createdAt": "2026-01-15T10:30:00",
  "expiresAt": "2026-02-14T10:30:00"
}
```

---

### Redirect
```bash
GET /{shortCode}
# Returns HTTP 302 redirect to original URL
```

---

### Get URL Stats
```bash
GET /api/stats/{shortCode}
```

**Response:**
```json
{
  "shortCode": "aB3kPqR",
  "shortUrl": "http://localhost:8080/aB3kPqR",
  "originalUrl": "https://www.example.com/very/long/url",
  "clickCount": 42,
  "createdAt": "2026-01-15T10:30:00",
  "expiresAt": "2026-02-14T10:30:00"
}
```

---

## Rate Limiting

Token bucket algorithm, enforced at the Spring interceptor layer.

- **Default limit:** 10 requests per 60 seconds
- **Keyed by:** `X-User-Id` header if present, falls back to client IP
- **Exceeded response:** `HTTP 429 Too Many Requests`
- **Bucket state** stored in Redis — works correctly across multiple app instances

```bash
# Test rate limiting — run 12 requests rapidly
for ($i=1; $i -le 12; $i++) {
    Invoke-WebRequest -Uri "http://localhost:8080/api/shorten" `
        -Method POST `
        -Headers @{"Content-Type"="application/json"; "X-User-Id"="testuser"} `
        -Body '{"originalUrl": "https://www.example.com"}' `
        -SkipHttpErrorCheck | Select-Object -ExpandProperty StatusCode
}
# Requests 1-10 → 201, Requests 11-12 → 429
```

---

## Kafka Analytics Pipeline

Every redirect fires an async Kafka event without blocking the response:

```
GET /{shortCode}
  → Redis / PostgreSQL lookup → 302 redirect returned immediately
  → @Async ClickEventProducer.publishClickEvent()
      → Kafka topic: click-events
      → ClickEventConsumer
      → INSERT into click_analytics
```

**click_analytics table schema:**
```sql
CREATE TABLE click_analytics (
    id           BIGSERIAL PRIMARY KEY,
    short_code   VARCHAR(20)  NOT NULL,
    original_url TEXT         NOT NULL,
    user_id      VARCHAR(100),
    clicked_at   TIMESTAMP    NOT NULL
);
```

---

## Project Structure

```
src/main/java/com/url_shortner/url_shortner/
│
├── config/
│   ├── RedisConfig.java
│   ├── KafkaProducerConfig.java
│   ├── KafkaConsumerConfig.java
│   └── WebMvcConfig.java
│
├── controller/
│   └── UrlController.java
│
├── dto/
│   ├── UrlRequest.java
│   └── UrlResponse.java
│
├── exception/
│   ├── UrlNotFoundException.java
│   ├── RateLimitExceededException.java
│   └── GlobalExceptionHandler.java
│
├── kafka/
│   ├── ClickEvent.java
│   ├── ClickEventProducer.java
│   └── ClickEventConsumer.java
│
├── model/
│   ├── UrlEntity.java
│   └── ClickAnalytics.java
│
├── ratelimit/
│   ├── TokenBucket.java
│   ├── RateLimiterService.java
│   └── RateLimitInterceptor.java
│
├── repository/
│   ├── UrlRepository.java
│   └── ClickAnalyticsRepository.java
│
├── service/
│   ├── UrlService.java
│   └── CacheService.java
│
└── UrlShortnerApplication.java
```

---

## Configuration

Key properties in `application.yaml`:

```yaml
app:
  base-url: http://localhost:8080
  short-code-length: 7
  cache-ttl-seconds: 3600
  rate-limit:
    max-requests: 10
    window-seconds: 60
```
