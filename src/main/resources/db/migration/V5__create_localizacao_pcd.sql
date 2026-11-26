-- Última localização conhecida de cada PCD_VISUAL (UMA linha por pessoa: só a mais recente, sem histórico).
-- Só é gravada enquanto existir ao menos um vínculo ATIVO com a permissão LOCALIZACAO (ver LocalizacaoService).
-- O responsável nunca lê esta tabela direto: o acesso passa por AutorizacaoVinculoService.exigirAcesso.
CREATE TABLE localizacao_pcd (
    pcd_id          BIGINT      NOT NULL PRIMARY KEY,
    latitude        DOUBLE      NOT NULL,
    longitude       DOUBLE      NOT NULL,
    precisao_metros DOUBLE      NULL,
    atualizado_em   DATETIME(6) NOT NULL,
    CONSTRAINT fk_localizacao_pcd FOREIGN KEY (pcd_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_localizacao_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_localizacao_longitude CHECK (longitude BETWEEN -180 AND 180)
);
