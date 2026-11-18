package com.atlas.backend_atlas.dto;

import com.atlas.backend_atlas.entity.Permissao;

import java.time.LocalDateTime;
import java.util.Set;

/** O código em texto puro só aparece aqui, uma única vez, para o PCD que o gerou. */
public record GerarCodigoResponse(
        String codigo,
        LocalDateTime expiraEm,
        long validadeSegundos,
        Set<Permissao> permissoes
) {}
