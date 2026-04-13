CREATE TABLE measurements (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    tipo VARCHAR(40) NOT NULL,
    valor DOUBLE PRECISION NOT NULL,
    data_registro TIMESTAMP NOT NULL
);

CREATE INDEX idx_measurements_user_id ON measurements (user_id);
CREATE INDEX idx_measurements_user_tipo ON measurements (user_id, tipo);
CREATE INDEX idx_measurements_data_registro ON measurements (data_registro);
