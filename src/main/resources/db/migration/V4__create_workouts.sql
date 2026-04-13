CREATE TABLE workouts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    grupo_muscular VARCHAR(30),
    fonte VARCHAR(20) NOT NULL,
    descricao_treino TEXT,
    data_geracao TIMESTAMP,
    data_realizacao TIMESTAMP,
    duracao_minutos INTEGER,
    intensidade_percebida INTEGER,
    observacoes TEXT
);

CREATE INDEX idx_workouts_user_id ON workouts (user_id);
CREATE INDEX idx_workouts_user_data_realizacao ON workouts (user_id, data_realizacao);
