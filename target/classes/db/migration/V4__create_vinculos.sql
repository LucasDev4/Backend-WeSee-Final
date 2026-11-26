-- Vínculo opcional entre PCD_VISUAL e RESPONSAVEL (nenhuma tabela existente é alterada).

-- Códigos temporários gerados pelo PCD. Só o HMAC do código é guardado (nunca o código em texto puro).
CREATE TABLE codigo_vinculacao (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo_hash   VARCHAR(64)  NOT NULL,
    pcd_id        BIGINT       NOT NULL,
    status        VARCHAR(15)  NOT NULL DEFAULT 'DISPONIVEL',
    permissoes    VARCHAR(200) NOT NULL,
    criado_em     DATETIME(6)  NOT NULL,
    expira_em     DATETIME(6)  NOT NULL,
    utilizado_em  DATETIME(6)  NULL,
    utilizado_por BIGINT       NULL,
    revogado_em   DATETIME(6)  NULL,
    CONSTRAINT uk_codigo_hash UNIQUE (codigo_hash),
    CONSTRAINT fk_codigo_pcd FOREIGN KEY (pcd_id) REFERENCES users (id),
    CONSTRAINT fk_codigo_utilizado_por FOREIGN KEY (utilizado_por) REFERENCES users (id),
    CONSTRAINT ck_codigo_status CHECK (status IN ('DISPONIVEL', 'UTILIZADO', 'EXPIRADO', 'REVOGADO'))
);
CREATE INDEX idx_codigo_pcd_status ON codigo_vinculacao (pcd_id, status);
CREATE INDEX idx_codigo_pcd_criado ON codigo_vinculacao (pcd_id, criado_em);

-- Vínculos. "chave_ativa" (coluna gerada) só existe quando o vínculo está ATIVO; o índice único nela
-- impede dois vínculos ATIVOS para o mesmo par (mesmo com requisições simultâneas) e ainda permite
-- manter o histórico de vínculos REVOGADOS e vincular de novo depois.
CREATE TABLE vinculo_responsavel (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    responsavel_id BIGINT      NOT NULL,
    pcd_id        BIGINT       NOT NULL,
    status        VARCHAR(15)  NOT NULL,
    permissoes    VARCHAR(200) NOT NULL,
    codigo_id     BIGINT       NULL,
    criado_por    BIGINT       NOT NULL,
    criado_em     DATETIME(6)  NOT NULL,
    atualizado_em DATETIME(6)  NOT NULL,
    autorizado_em DATETIME(6)  NULL,
    revogado_em   DATETIME(6)  NULL,
    revogado_por  BIGINT       NULL,
    chave_ativa   VARCHAR(41) GENERATED ALWAYS AS (
        CASE WHEN status = 'ATIVO' THEN CONCAT(responsavel_id, ':', pcd_id) ELSE NULL END
    ) STORED,
    CONSTRAINT uk_vinculo_ativo UNIQUE (chave_ativa),
    CONSTRAINT fk_vinculo_responsavel FOREIGN KEY (responsavel_id) REFERENCES users (id),
    CONSTRAINT fk_vinculo_pcd FOREIGN KEY (pcd_id) REFERENCES users (id),
    CONSTRAINT fk_vinculo_codigo FOREIGN KEY (codigo_id) REFERENCES codigo_vinculacao (id),
    CONSTRAINT fk_vinculo_criado_por FOREIGN KEY (criado_por) REFERENCES users (id),
    CONSTRAINT fk_vinculo_revogado_por FOREIGN KEY (revogado_por) REFERENCES users (id),
    CONSTRAINT ck_vinculo_status CHECK (status IN ('PENDENTE', 'ATIVO', 'REVOGADO')),
    CONSTRAINT ck_vinculo_pessoas CHECK (responsavel_id <> pcd_id)
);
CREATE INDEX idx_vinculo_responsavel ON vinculo_responsavel (responsavel_id, status);
CREATE INDEX idx_vinculo_pcd ON vinculo_responsavel (pcd_id, status);

-- Tentativas FALHAS de usar um código (proteção contra adivinhação por força bruta).
CREATE TABLE tentativa_vinculacao (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL,
    ocorrido_em DATETIME(6) NOT NULL,
    CONSTRAINT fk_tentativa_usuario FOREIGN KEY (usuario_id) REFERENCES users (id)
);
CREATE INDEX idx_tentativa_usuario ON tentativa_vinculacao (usuario_id, ocorrido_em);
