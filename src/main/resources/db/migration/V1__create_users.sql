CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       telegram_chat_id BIGINT NOT NULL UNIQUE,
                       nome VARCHAR(100),
                       objetivo VARCHAR(30),
                       nivel VARCHAR(30),
                       frequencia_treino_estimada INTEGER,
                       peso_atual DOUBLE PRECISION,
                       altura_cm INTEGER,
                       onboarding_concluido BOOLEAN NOT NULL DEFAULT FALSE,
                       nudges_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                       last_interaction_at TIMESTAMP,
                       last_nudge_at TIMESTAMP,
                       created_at TIMESTAMP NOT NULL,
                       updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_users_last_interaction_at ON users (last_interaction_at);
