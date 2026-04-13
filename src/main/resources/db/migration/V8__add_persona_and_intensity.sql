-- V8: Adiciona campos de persona motivacional e nivel de intensidade ao usuario

ALTER TABLE users ADD COLUMN IF NOT EXISTS persona VARCHAR(30) DEFAULT 'COACH_AMIGO';
ALTER TABLE users ADD COLUMN IF NOT EXISTS intensity_level VARCHAR(20) DEFAULT 'MODERADO';
