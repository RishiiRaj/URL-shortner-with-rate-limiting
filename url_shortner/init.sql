CREATE TABLE IF NOT EXISTS urls (
    id              BIGSERIAL PRIMARY KEY,
    short_code      VARCHAR(10)  NOT NULL UNIQUE,
    original_url    TEXT         NOT NULL,
    user_id         VARCHAR(50),
    click_count     BIGINT       DEFAULT 0,
    created_at      TIMESTAMP    DEFAULT NOW(),
    expires_at      TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_short_code ON urls(short_code);
CREATE INDEX IF NOT EXISTS idx_user_id ON urls(user_id);

CREATE TABLE IF NOT EXISTS click_analytics (
    id          BIGSERIAL PRIMARY KEY,
    short_code  VARCHAR(20)  NOT NULL,
    original_url TEXT        NOT NULL,
    user_id     VARCHAR(100),
    clicked_at  TIMESTAMP    NOT NULL
);