package com.atlas.backend_atlas.dto;

import com.atlas.backend_atlas.entity.Permissao;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record AtualizarPermissoesRequest(
        @NotNull(message = "Permissões são obrigatórias (use [] para não compartilhar nada)")
        Set<Permissao> permissoes
) {}
