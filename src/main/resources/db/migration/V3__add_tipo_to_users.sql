-- Tipo de conta usado pelo app WeSee: PCD_VISUAL (usuário) ou RESPONSAVEL.
ALTER TABLE users
    ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'PCD_VISUAL' AFTER role;
