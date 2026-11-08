-- V32: aparelhos moveis (token de push FCM) e preferencias de notificacao push.
-- O token do aparelho nao e PII de cliente; ainda assim nunca e logado em claro.

CREATE TABLE user_devices (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token        VARCHAR(1024) NOT NULL,
    platform     VARCHAR(10) NOT NULL,
    app_version  VARCHAR(30),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    revoked_at   TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_user_devices_token UNIQUE (token),
    CONSTRAINT ck_user_devices_platform CHECK (platform IN ('ANDROID', 'IOS', 'WEB'))
);

CREATE INDEX idx_user_devices_user_active ON user_devices (user_id) WHERE revoked_at IS NULL;

CREATE TABLE user_push_preferences (
    user_id         UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    critical_alerts BOOLEAN NOT NULL DEFAULT TRUE,
    deadlines       BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
