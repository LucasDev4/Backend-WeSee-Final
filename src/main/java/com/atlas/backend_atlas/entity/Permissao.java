package com.atlas.backend_atlas.entity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Categorias de dados da pessoa PCD que o responsável pode consultar. Quem decide é o PCD. */
public enum Permissao {
    LOCALIZACAO,
    STATUS_DISPOSITIVO,
    ALERTAS,
    HISTORICO_ROTAS;

    /** Padrão quando o PCD não escolhe: tudo, exceto o histórico de rotas (mais sensível: precisa ser ativado). */
    public static final Set<Permissao> PADRAO =
            Collections.unmodifiableSet(EnumSet.of(LOCALIZACAO, STATUS_DISPOSITIVO, ALERTAS));
}
