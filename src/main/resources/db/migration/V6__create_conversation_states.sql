-- V6: Cria tabela conversation_states (estado conversacional separado do User)
-- Decisão arquitetural TCC2: estado da conversa é entidade própria com partialData JSONB

CREATE TABLE conversation_states (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    current_flow  VARCHAR(50),
    current_step  INTEGER,
    partial_data  JSONB NOT NULL DEFAULT '{}',
    updated_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversation_states_user_id ON conversation_states(user_id);

-- Migra dados existentes de usuários que tinham fluxo ativo na V5
INSERT INTO conversation_states (user_id, current_flow, current_step, updated_at)
SELECT id, current_flow, current_step, updated_at
FROM users
WHERE current_flow IS NOT NULL AND current_flow != 'NONE';
