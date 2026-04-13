-- V7: Remove colunas de estado conversacional do users (migradas para conversation_states na V6)

ALTER TABLE users DROP COLUMN IF EXISTS current_flow;
ALTER TABLE users DROP COLUMN IF EXISTS current_step;
