package com.atlas.backend_atlas.dto;

import com.atlas.backend_atlas.entity.Permissao;

import java.util.Set;

/** Corpo opcional: o PCD escolhe o que o responsável poderá ver. Sem corpo = permissões padrão. */
public record GerarCodigoRequest(Set<Permissao> permissoes) {}
