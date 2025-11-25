CREATE TABLE messages (
                          id BIGSERIAL PRIMARY KEY,
                          user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
                          conteudo TEXT NOT NULL,
                          tipo VARCHAR(20) NOT NULL,
                          data_hora TIMESTAMP NOT NULL
);

CREATE INDEX idx_messages_user_id ON messages (user_id);
CREATE INDEX idx_messages_data_hora ON messages (data_hora);
